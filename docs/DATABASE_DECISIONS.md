# Database Architecture Decisions

This document outlines the rationale behind the relational database schema design for the LoanFlow Phase 1 implementation.

## 1. Concurrency, Data Integrity, and Locking
- **Optimistic Locking (`version` column):** JPA/Hibernate utilizes an integer `version` column on critical tables (`users`, `loan_applications`, `loans`, `repayment_schedules`, `payments`, `credit_reviews`, `manager_reviews`) to throw an `OptimisticLockException` if concurrent modifications occur.
- **Transactional Row Locks:** Updates to `repayment_schedules.amount_paid` require pessimistic row-level locking (e.g., `SELECT ... FOR UPDATE`) in the Service layer during payment allocation to ensure atomicity and exact balance calculation.
- **No Destructive Cascades:** `ON DELETE CASCADE` is intentionally omitted from all Foreign Keys. In an auditable system, `ON DELETE RESTRICT` guarantees referential integrity.
- **Constraints & Positivity:** MySQL 8.0.16+ `CHECK` constraints explicitly enforce positive bounds (e.g., `interest_rate >= 0.0000 AND interest_rate <= 1.0000`, `allocated_amount > 0`) and ensure `amount_paid <= total_amount`.
- **Monetary Precision:** The `DECIMAL(19,4)` type ensures precise financial storage, mapping to Java's `BigDecimal`. Interest rates use `DECIMAL(5,4)` (e.g., 5% = 0.0500).

## 2. Payments and Transactional Invariants
- **Payment Request Hash & Idempotency:** Both Payments and Refunds store a canonical SHA-256 `request_hash` alongside a customer-scoped `idempotency_key` and a globally unique `reference_number`. 
    - *Replay:* If a request arrives with an existing key and matching hash, the system returns the existing response.
    - *Conflict:* If the key matches but the hash differs, the system returns a conflict error.
- **Payment Lifecycle & Status Behavior:** 
    - **PENDING**: The payment or refund is initiated but unconfirmed. For payments, `unapplied_amount` equals `amount`. No allocations are created.
    - **SUCCESS**: The transaction clears. The system applies allocations atomically under a row lock.
    - **FAILED**: The transaction failed to clear. `unapplied_amount` equals `amount`, and no allocations are made.
- **Original Payment Invariants:**
    - Only `SUCCESS` payments generate `payment_allocations`.
    - `payments.unapplied_amount` acts as an immutable historical record of the funds *not* allocated to a schedule at the time of processing.
    - `payments.amount = unapplied_amount + SUM(payment_allocations.allocated_amount)` must mathematically hold true.
- **Refunds and Reversals Invariants:**
    - Refunds are created as new `payments` records with `type` = `'REFUND'` and a positive `amount`, linked to the original payment via `original_payment_id`.
    - **Integrity Validation (Service Layer):** Only `SUCCESS` original `PAYMENT` records can be refunded. A `REFUND` record itself cannot be refunded. The customer and loan ownership must exactly match the original payment.
    - **Refund Allocations:** Instead of modifying historical `payment_allocations`, `refund_allocations` are inserted. The composite foreign key `(original_allocation_id, repayment_schedule_id)` guarantees the refund strictly targets the exact same schedule as the original allocation.
    - **Refund Accounting:** For a REFUND record, `payments.amount = refunded_unapplied_amount + SUM(refund_allocations.refunded_amount)`.
    - **Unapplied Funds:** The currently available refundable overpayment is calculated as `original_payment.unapplied_amount - original_payment.refunded_unapplied_amount`. Refunds against unapplied funds increment `refunded_unapplied_amount`.
- **Schedule Balances and Loan Closure:** 
    - The net outstanding balance of an installment is `total_amount - amount_paid + amount_refunded`.
    - If the net balance is `<= 0`, the schedule status is `PAID`. Once all schedules are `PAID`, the loan `status` transitions to `CLOSED`.
- **Concurrent Locking Strategy:** To prevent over-refunds and deadlocks, transactions must acquire locks in a consistent order: Lock Original `payments` row -> Lock `repayment_schedules` (ordered by ID ASC). The service validates that `amount_refunded <= amount_paid` and `SUM(refund_allocations.refunded_amount) <= original_allocation.allocated_amount`.


## 3. Immutability, Lifecycle, and Snapshots
- **Approved Principal Amount:** The `loan_applications` table includes an `approved_amount`. Managers have the authority to approve an amount smaller than or equal to the `requested_amount`. This field is assigned *only* upon manager approval and remains `NULL` if the application is not approved. Cross-table service validation strictly enforces that `loans.principal_amount` and `disbursements.amount` must exactly equal `loan_applications.approved_amount`.
- **Loan Lifecycle (`APPROVED` -> `DISBURSED` -> `ACTIVE` -> `CLOSED`):** 
    - A loan is created in the `APPROVED` (pre-disbursement) state immediately upon application manager approval.
    - A corresponding `disbursements` record is scheduled. 
    - **External Disbursement Atomicity:** External API calls to a payment gateway cannot be atomic with local MySQL transactions. The system generates a stable, deterministic external idempotency key (e.g., `loan_<id>_disb`) sent to the gateway and reused across all retries. The `disbursement_attempts` table records the `PENDING` state before the call. Upon success, the attempt is marked `SUCCESS`. Crash recovery jobs poll `PENDING` attempts and must reconcile uncertain outcomes by querying the gateway with the idempotency key *before* retrying, strictly preventing duplicate payouts.
    - When the disbursement successfully clears, the loan state shifts to `DISBURSED`.
    - This triggers the generation of `repayment_schedules`. Schedule generation is an atomic transaction. If it fails mid-way, it completely rolls back, preventing partial generation. It is idempotent by skipping generation entirely if schedules already exist for the loan. It never deletes schedules already referenced by payments or audit records. The loan shifts to `ACTIVE` once schedules are successfully committed.
- **Disbursement Attempt Semantics:** `disbursements` enforces a unique 1:1 `loan_id`. To preserve this while allowing retries and crash recovery, the system utilizes the `disbursement_attempts` append-only table to track individual API calls (PENDING, SUCCESS, FAILED) with `started_at` and `completed_at` timestamps. The main `disbursements` record is optimistically updated with the latest aggregated status.
- **Loan Contract Snapshots:** The `loans` table explicitly copies the `interest_rate` and `interest_calculation_method` from the `loan_products` configuration at the time of origination. Product modifications by an ADMIN will never retroactively affect active loans.
- **Application History:** The `application_status_history` creates an atomic, append-only immutable ledger. `previous_status` is `NULL` only during the initial DRAFT creation. All state transitions (including resubmissions for `ADDITIONAL_INFO_REQUIRED`) are durably recorded.
- **Latest Review Ordering & Prerequisites:** Document and application reviews permit multiple attempts. Deterministic ordering relies on `ORDER BY reviewed_at DESC, id DESC`. Furthermore, reviews require prerequisite validation (e.g., a document cannot be reviewed if its `upload_status` is not `COMPLETED`).
- **Tokens and Documents:** Raw tokens are never stored; only their cryptographic hashes reside in `refresh_tokens`/`password_reset_tokens`. File contents are not stored in the database; `documents` stores metadata and a `UNIQUE(storage_key)` referencing private S3 storage.

## 4. Separation of Duties and Access Consistency
- **Cross-Module Ownership Consistency:** Deeply nested relationships enforce strict ownership via Composite Unique constraints and Foreign Keys. 
    - e.g., `documents(application_id, customer_id)` explicitly references `loan_applications(id, customer_id)` preventing a scenario where a document points to Application A but Customer B.
- **Review Segregation:** The rule that a Manager cannot be the same person as the Credit Officer is enforced as a transactional validation in the Java Service layer, as it requires cross-table row introspection unsupported by simple SQL constraints.

## 5. Audit and Outbox Reliability
- **Append-Only Audit:** `audit_logs` are strictly append-only. Sensitive fields and secrets (like passwords or tokens) are explicitly excluded from JSON payloads in the Application layer before persistence.
- **Transactional Outbox Polling:** `outbox_events` uses `leased_by` and `leased_until` timestamps. This allows multiple Kafka publisher worker nodes to claim rows without race conditions, and supports crash recovery by re-claiming expired leases.


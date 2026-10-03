# LoanFlow Backend

This is the Spring Boot backend module for the LoanFlow project (Phase 1).

## Prerequisites

- Java 21
- Maven 3.8+
- Docker and Docker Compose (for local database and Redis)

## Environment Variables

The application requires the following environment variables to run. Do **not** commit real values to version control. You can set these in your local run configuration or via a `.env` file (which is gitignored).

```properties
# Database
DB_URL=jdbc:mysql://localhost:3306/loanflow?useSSL=false&allowPublicKeyRetrieval=true
DB_USERNAME=loanflow_user
DB_PASSWORD=loanflow_pass

# Redis
REDIS_HOST=localhost
REDIS_PORT=6379

# Security
JWT_SECRET=generate-a-secure-256-bit-secret-key-here

# S3 Storage (MinIO or AWS)
S3_ENDPOINT=http://localhost:9000
S3_REGION=us-east-1
S3_BUCKET=loanflow-docs
S3_ACCESS_KEY=your_access_key
S3_SECRET_KEY=your_secret_key
```

## Running Local Infrastructure

To start the local MySQL and Redis instances, use Docker Compose:

```bash
cd loanflow-backend
docker-compose up -d
```

This will start:
- MySQL 8.0 on port 3306 (User: `loanflow_user`, Password: `loanflow_pass`, DB: `loanflow`)
- Redis on port 6379

## Running the Application

You can run the application using Maven:

```bash
cd loanflow-backend
mvn spring-boot:run
```

By default, the application runs on port `8080`. Flyway is configured to `validate` the schema, and Hibernate `ddl-auto` is set to `validate`. No tables will be automatically created on startup.

## Verifying the Application

Check the health endpoint provided by Spring Boot Actuator:

```
GET http://localhost:8080/actuator/health
```

Check the OpenAPI Swagger UI:

```
GET http://localhost:8080/swagger-ui.html
```

## Database Migrations (Flyway)

Flyway is configured to run automatically on application startup. However, in local development, you can manage migrations manually:

**Run Migrations:**
```bash
mvn flyway:migrate
```

**Verify Migration History:**
```bash
mvn flyway:info
```

**Reset Local Database Safely:**
```bash
mvn flyway:clean flyway:migrate
```
*(Note: `flyway:clean` is disabled in production environments)*

## Database Integration Tests

To run tests against a real MySQL instance (requires Docker/Testcontainers):
```bash
mvn test -Dspring.profiles.active=test-db
```

## Phase 3 Task 8 — Eligibility Assessment Engine

The LoanFlow backend includes a deterministic, explainable demo eligibility assessment engine for submitted loan applications.

> **Disclaimer**: This is an educational software demonstration and is **not** a real lending decision system, credit approval, or loan offer.

### Rules Evaluated (Ruleset Version `1.0.0`)
1. **Rule A — Application Amount**: Validates that requested loan amount is within product limits (`minAmount` - `maxAmount`). Emits `AMOUNT_BELOW_PRODUCT_MIN` or `AMOUNT_ABOVE_PRODUCT_MAX`.
2. **Rule B — Loan Tenure**: Validates that requested loan term is within product limits (`minTermMonths` - `maxTermMonths`). Emits `TENURE_BELOW_PRODUCT_MIN` or `TENURE_ABOVE_PRODUCT_MAX`.
3. **Rule C — Required Documents**: Checks whether mandatory required document types for the product have been uploaded and reviewed. Emits `REQUIRED_DOCUMENT_MISSING`, `REQUIRED_DOCUMENT_PENDING`, or `REQUIRED_DOCUMENT_REJECTED`.
4. **Rule D — Application State**: Requires application to be in `SUBMITTED` status. Applications in `DRAFT` status return HTTP 409 CONFLICT.

### Endpoints
- `POST /api/v1/loan-applications/{applicationId}/eligibility-assessments` — Evaluates rules and persists assessment result.
- `GET /api/v1/loan-applications/{applicationId}/eligibility-assessments` — Retrieves assessment history for customer's application.

## Phase 3 Task 9 — Credit Officer Review Workflow

The LoanFlow backend provides a secure, auditable credit officer review workflow for submitted loan applications.

> **Disclaimer**: This is an educational software demonstration. Recommendations (`RECOMMEND`, `DO_NOT_RECOMMEND`, `REQUEST_INFORMATION`) are workflow review records and are **not** actual lending decisions, credit bureau approvals, or final loan approvals.

### Workflow & Prerequisites
- Endpoints require JWT authentication and `CREDIT_OFFICER` role.
- Applications must be in `SUBMITTED` status and have a completed eligibility assessment before credit review.
- One review per application is enforced.
- Attempting to `RECOMMEND` an application with `INELIGIBLE` or `INCOMPLETE` eligibility assessment returns HTTP 409 CONFLICT.
- Atomically persists the `CreditReview`, `AuditLog`, and `OutboxEvent` in a single transaction.

### Endpoints
- `GET /api/v1/credit-reviews/queue` — Paginated queue of submitted applications with completed eligibility assessments awaiting review.
- `GET /api/v1/credit-reviews/applications/{applicationId}` — Comprehensive review context (application, customer summary, product rules, eligibility assessment, document statuses, existing review).
- `POST /api/v1/credit-reviews/applications/{applicationId}` — Record credit officer review recommendation (`RECOMMEND`, `DO_NOT_RECOMMEND`, `REQUEST_INFORMATION`) and comments.

## Phase 3 Task 10 — Manager Review & Approval Workflow

The LoanFlow backend provides a secure, auditable manager review workflow for evaluating credit officer recommendations and creating demo loan records upon approval.

> **Disclaimer**: This is an educational software demonstration. Manager decisions (`APPROVE`, `REJECT`) are workflow records and are **not** real-world lending approvals, loan offers, or legally binding financial commitments.

### Workflow & Prerequisites
- Endpoints require JWT authentication and `MANAGER` role.
- Applications must have a completed `CreditReview` before manager evaluation.
- Approving an application automatically creates a demo `Loan` entity with loan amount, interest rate, term months, and transitions application status to `APPROVED`.
- Rejecting an application transitions application status to `REJECTED`.
- Atomically persists `ManagerReview`, `LoanApplication` status, `Loan` entity (if approved), `AuditLog`, and `OutboxEvent`.

### Endpoints
- `GET /api/v1/manager-reviews/queue` — Paginated queue of applications with completed credit officer reviews awaiting manager decision.
- `GET /api/v1/manager-reviews/applications/{applicationId}` — Manager decision context including credit recommendation, eligibility result, and customer details.
- `POST /api/v1/manager-reviews/applications/{applicationId}` — Record manager decision (`APPROVE`, `REJECT`) with comments.

## Phase 3 Task 11 — Loan Disbursement Workflow (Demo)

The LoanFlow backend provides a secure, transactional, simulated loan disbursement workflow using the `Disbursement` and `DisbursementAttempt` entities.

> **Disclaimer**: This is an educational software demonstration. **No real money is transferred**, no banking/payment gateway APIs are called, no bank credentials are collected, and no actual financial obligations or repayment collections are created. All disbursement outcomes are clearly labeled as simulated demo operations (`isDemo: true`).

### Workflow & Security Rules
- Operational queue (`GET /queue`) and simulated disbursement initiation (`POST /loans/{loanId}`) require JWT authentication and `ADMIN` role.
- Disbursement lookup endpoints (`GET /{disbursementId}`, `GET /{disbursementId}/attempts`) permit `ADMIN` or `MANAGER` role.
- Customers can view their own application's disbursement status via `GET /api/v1/loan-applications/{applicationId}/disbursement` (requires `CUSTOMER` role and verified ownership).
- Only manager-approved loans (`status: "APPROVED"`) can be disbursed.
- Maximum 3 retry attempts per disbursement are enforced (`MAX_RETRY_ATTEMPTS = 3`).
- Durable database-backed idempotency is supported via the optional `X-Idempotency-Key` header and persisted in the `disbursement_idempotency_keys` table (Flyway migration `V7__create_disbursement_idempotency_table.sql`). Reusing a key with an identical request payload returns the original operation result (surviving application restarts); reusing a key with a different payload returns HTTP 409 CONFLICT.
- Concurrent requests are handled safely via DB-level pessimistic locking (`SELECT ... FOR UPDATE` on loans) and UNIQUE constraints (`disbursements.loan_id` and `disbursement_idempotency_keys.idempotency_key`).
- Unrestricted client control over simulation outcomes is removed. Standard requests execute deterministically, while controlled test mechanisms (`X-Test-Simulate-Failure` header or ThreadLocal test overrides) are used for automated test suites.
- Successful simulated disbursement transitions both the `Disbursement` and `Loan` status to `DISBURSED`, while persisting `DisbursementAttempt`, `AuditLog`, and `OutboxEvent` in a single atomic database transaction.

### Endpoints
- `GET /api/v1/disbursements/queue` — Paginated queue of approved loans awaiting simulated disbursement (`ADMIN` role).
- `POST /api/v1/disbursements/loans/{loanId}` — Initiate simulated demo disbursement (`ADMIN` role).
- `GET /api/v1/disbursements/{disbursementId}` — View safe disbursement metadata and status (`ADMIN`, `MANAGER` role).
- `GET /api/v1/disbursements/{disbursementId}/attempts` — View disbursement processing attempt history (`ADMIN`, `MANAGER` role).
- `GET /api/v1/loan-applications/{applicationId}/disbursement` — Customer owner view of loan disbursement status (`CUSTOMER` role).




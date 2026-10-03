# Relational Database Schema Design

This document details the exact table structures, constraints, and relationships for the LoanFlow Phase 1 database.

**Global Conventions:**
- MySQL Engine: `InnoDB`
- Timestamps: `DATETIME(6)` (storing UTC time)
- Monetary Values: `DECIMAL(19,4)` represented by `BigDecimal` in Java.
- Interest Rates: Stored as `DECIMAL(5,4)` (e.g., 0.0500 for 5%) represented by `BigDecimal` in Java.
- Checks: MySQL 8.0.16+ `CHECK` constraints are explicitly required for numeric bounds.
- Optimistic Locking: `version INT NOT NULL DEFAULT 0` (mapped via JPA `@Version`).
- Deletes: `ON DELETE RESTRICT` for all Foreign Keys.

## 1. Auth & User Module
### `users`
- `id` BIGINT AUTO_INCREMENT PK
- `email` VARCHAR(255) NOT NULL UNIQUE
- `password_hash` VARCHAR(255) NOT NULL
- `is_active` BOOLEAN NOT NULL DEFAULT TRUE
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL

### `roles`
- `id` INT AUTO_INCREMENT PK
- `name` VARCHAR(50) NOT NULL UNIQUE

### `user_roles`
- `user_id` BIGINT NOT NULL FK (`users.id`)
- `role_id` INT NOT NULL FK (`roles.id`)
- **PK**: `(user_id, role_id)`

### `customer_profiles`
- `id` BIGINT AUTO_INCREMENT PK
- `user_id` BIGINT NOT NULL UNIQUE FK (`users.id`)
- `first_name` VARCHAR(100) NOT NULL
- `last_name` VARCHAR(100) NOT NULL
- `dob` DATE NOT NULL
- `phone` VARCHAR(20) NOT NULL
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL

### `refresh_tokens`
- `id` BIGINT AUTO_INCREMENT PK
- `user_id` BIGINT NOT NULL FK (`users.id`)
- `token_hash` VARCHAR(255) NOT NULL UNIQUE
- `expires_at` DATETIME(6) NOT NULL
- `revoked_at` DATETIME(6) NULL
- `created_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_rt_user_expires (user_id, expires_at)`

### `password_reset_tokens`
- `id` BIGINT AUTO_INCREMENT PK
- `user_id` BIGINT NOT NULL FK (`users.id`)
- `token_hash` VARCHAR(255) NOT NULL UNIQUE
- `expires_at` DATETIME(6) NOT NULL
- `used_at` DATETIME(6) NULL
- `created_at` DATETIME(6) NOT NULL

## 2. Product Module
### `loan_products`
- `id` BIGINT AUTO_INCREMENT PK
- `name` VARCHAR(100) NOT NULL UNIQUE
- `description` TEXT NULL
- `min_amount` DECIMAL(19,4) NOT NULL
- `max_amount` DECIMAL(19,4) NOT NULL
- `interest_rate` DECIMAL(5,4) NOT NULL
- `min_term_months` INT NOT NULL
- `max_term_months` INT NOT NULL
- `is_active` BOOLEAN NOT NULL DEFAULT TRUE
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL
- **CHECK**: `min_amount > 0 AND max_amount >= min_amount`
- **CHECK**: `interest_rate >= 0.0000 AND interest_rate <= 1.0000`
- **CHECK**: `min_term_months > 0 AND max_term_months >= min_term_months`

### `product_required_documents`
- `id` BIGINT AUTO_INCREMENT PK
- `product_id` BIGINT NOT NULL FK (`loan_products.id`)
- `document_type` VARCHAR(50) NOT NULL
- `is_mandatory` BOOLEAN NOT NULL DEFAULT TRUE
- **UNIQUE**: `(product_id, document_type)`

## 3. Application Module
### `loan_applications`
- `id` BIGINT AUTO_INCREMENT PK
- `customer_id` BIGINT NOT NULL FK (`customer_profiles.id`)
- `product_id` BIGINT NOT NULL FK (`loan_products.id`)
- `requested_amount` DECIMAL(19,4) NOT NULL
- `approved_amount` DECIMAL(19,4) NULL
- `term_months` INT NOT NULL
- `status` VARCHAR(50) NOT NULL
- `purpose` TEXT NULL
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL
- **CHECK**: `requested_amount > 0 AND term_months > 0`
- **CHECK**: `approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= requested_amount)`
- **UNIQUE**: `(id, customer_id)` (For composite FK ownership enforcement)
- **UNIQUE**: `(id, customer_id, product_id)` (For composite FK ownership enforcement)
- **INDEX**: `idx_la_status (status)`

### `application_status_history`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL FK (`loan_applications.id`)
- `previous_status` VARCHAR(50) NULL (NULL permitted for initial DRAFT creation)
- `new_status` VARCHAR(50) NOT NULL
- `reason` TEXT NULL
- `actor_id` BIGINT NOT NULL FK (`users.id`)
- `created_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_ash_app_created (application_id, created_at DESC)`

## 4. Document Module
### `documents`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL
- `customer_id` BIGINT NOT NULL
- `document_type` VARCHAR(50) NOT NULL
- `original_filename` VARCHAR(255) NOT NULL
- `content_type` VARCHAR(100) NOT NULL
- `file_size` BIGINT NOT NULL
- `checksum` VARCHAR(255) NOT NULL
- `storage_key` VARCHAR(512) NOT NULL UNIQUE
- `upload_status` VARCHAR(50) NOT NULL (e.g., PENDING, COMPLETED, FAILED)
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL
- **CHECK**: `file_size > 0`
- **FK**: `(application_id, customer_id)` references `loan_applications(id, customer_id)`

### `document_reviews`
- `id` BIGINT AUTO_INCREMENT PK
- `document_id` BIGINT NOT NULL FK (`documents.id`)
- `reviewer_id` BIGINT NOT NULL FK (`users.id`)
- `status` VARCHAR(50) NOT NULL (VERIFIED, REJECTED)
- `comments` TEXT NULL
- `reviewed_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_dr_doc_reviewed (document_id, reviewed_at DESC)`

## 5. Eligibility Module
### `eligibility_assessments`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL FK (`loan_applications.id`)
- `ruleset_version` VARCHAR(50) NOT NULL
- `decision` VARCHAR(20) NOT NULL (PASS, FAIL)
- `input_snapshot` JSON NOT NULL
- `assessed_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_ea_app_assessed (application_id, assessed_at DESC)`

### `eligibility_reason_codes`
- `id` BIGINT AUTO_INCREMENT PK
- `assessment_id` BIGINT NOT NULL FK (`eligibility_assessments.id`)
- `code` VARCHAR(50) NOT NULL
- `description` TEXT NOT NULL

## 6. Review Module
### `credit_reviews`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL FK (`loan_applications.id`)
- `credit_officer_id` BIGINT NOT NULL FK (`users.id`)
- `decision` VARCHAR(50) NOT NULL (APPROVED, REJECTED, ADDITIONAL_INFO_REQUIRED)
- `comments` TEXT NOT NULL
- `version` INT NOT NULL DEFAULT 0
- `reviewed_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_cr_app_reviewed (application_id, reviewed_at DESC, id DESC)`

### `manager_reviews`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL FK (`loan_applications.id`)
- `manager_id` BIGINT NOT NULL FK (`users.id`)
- `decision` VARCHAR(50) NOT NULL (APPROVED, REJECTED)
- `comments` TEXT NOT NULL
- `version` INT NOT NULL DEFAULT 0
- `reviewed_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_mr_app_reviewed (application_id, reviewed_at DESC, id DESC)`

## 7. Loan Module
### `loans`
- `id` BIGINT AUTO_INCREMENT PK
- `application_id` BIGINT NOT NULL UNIQUE
- `customer_id` BIGINT NOT NULL
- `product_id` BIGINT NOT NULL
- `principal_amount` DECIMAL(19,4) NOT NULL
- `interest_rate` DECIMAL(5,4) NOT NULL
- `interest_calculation_method` VARCHAR(50) NOT NULL
- `term_months` INT NOT NULL
- `status` VARCHAR(50) NOT NULL (APPROVED, DISBURSED, ACTIVE, CLOSED)
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL
- **CHECK**: `principal_amount > 0 AND term_months > 0`
- **CHECK**: `interest_rate >= 0.0000 AND interest_rate <= 1.0000`
- **UNIQUE**: `(id, customer_id)` (For payment ownership mapping)
- **FK**: `(application_id, customer_id, product_id)` references `loan_applications(id, customer_id, product_id)`

### `repayment_schedules`
- `id` BIGINT AUTO_INCREMENT PK
- `loan_id` BIGINT NOT NULL FK (`loans.id`)
- `installment_number` INT NOT NULL
- `due_date` DATE NOT NULL
- `principal_amount` DECIMAL(19,4) NOT NULL
- `interest_amount` DECIMAL(19,4) NOT NULL
- `total_amount` DECIMAL(19,4) NOT NULL
- `amount_paid` DECIMAL(19,4) NOT NULL DEFAULT 0.0000
- `amount_refunded` DECIMAL(19,4) NOT NULL DEFAULT 0.0000
- `status` VARCHAR(50) NOT NULL (PENDING, PARTIAL, PAID, OVERDUE)
- `version` INT NOT NULL DEFAULT 0
- **CHECK**: `principal_amount >= 0 AND interest_amount >= 0`
- **CHECK**: `total_amount = principal_amount + interest_amount`
- **CHECK**: `amount_paid >= 0 AND amount_paid <= total_amount`
- **CHECK**: `amount_refunded >= 0 AND amount_refunded <= amount_paid`
- **UNIQUE**: `(loan_id, installment_number)`
- **UNIQUE**: `(id, loan_id)` (For allocation mapping)
- **INDEX**: `idx_rs_loan_status (loan_id, status)`

## 8. Payment & Disbursement Module
### `disbursements`
- `id` BIGINT AUTO_INCREMENT PK
- `loan_id` BIGINT NOT NULL UNIQUE FK (`loans.id`)
- `amount` DECIMAL(19,4) NOT NULL
- `status` VARCHAR(50) NOT NULL (PENDING, SUCCESS, FAILED)
- `version` INT NOT NULL DEFAULT 0
- `disbursed_at` DATETIME(6) NULL
- `created_at` DATETIME(6) NOT NULL
- **CHECK**: `amount > 0`

### `disbursement_attempts`
- `id` BIGINT AUTO_INCREMENT PK
- `disbursement_id` BIGINT NOT NULL FK (`disbursements.id`)
- `attempt_number` INT NOT NULL
- `status` VARCHAR(50) NOT NULL (PENDING, SUCCESS, FAILED)
- `failure_reason` TEXT NULL
- `started_at` DATETIME(6) NOT NULL
- `completed_at` DATETIME(6) NULL
- **UNIQUE**: `(disbursement_id, attempt_number)`

### `payments`
- `id` BIGINT AUTO_INCREMENT PK
- `loan_id` BIGINT NOT NULL
- `customer_id` BIGINT NOT NULL
- `type` VARCHAR(50) NOT NULL DEFAULT 'PAYMENT' (PAYMENT, REFUND)
- `original_payment_id` BIGINT NULL FK (`payments.id`)
- `amount` DECIMAL(19,4) NOT NULL
- `unapplied_amount` DECIMAL(19,4) NOT NULL DEFAULT 0.0000
- `refunded_unapplied_amount` DECIMAL(19,4) NOT NULL DEFAULT 0.0000
- `payment_date` DATETIME(6) NOT NULL
- `idempotency_key` VARCHAR(100) NOT NULL
- `request_hash` CHAR(64) NOT NULL
- `reference_number` VARCHAR(100) NOT NULL UNIQUE
- `status` VARCHAR(50) NOT NULL (PENDING, SUCCESS, FAILED)
- `version` INT NOT NULL DEFAULT 0
- `created_at` DATETIME(6) NOT NULL
- `updated_at` DATETIME(6) NOT NULL
- **CHECK**: `amount > 0`
- **CHECK**: `unapplied_amount >= 0 AND unapplied_amount <= amount`
- **CHECK**: `refunded_unapplied_amount >= 0 AND refunded_unapplied_amount <= unapplied_amount`
- **UNIQUE**: `(customer_id, idempotency_key)`
- **UNIQUE**: `(id, loan_id)` (For allocation mapping)
- **FK**: `(loan_id, customer_id)` references `loans(id, customer_id)`
- **INDEX**: `idx_pay_loan_status (loan_id, status)`

### `payment_allocations`
- `id` BIGINT AUTO_INCREMENT PK
- `payment_id` BIGINT NOT NULL
- `repayment_schedule_id` BIGINT NOT NULL
- `loan_id` BIGINT NOT NULL
- `allocated_amount` DECIMAL(19,4) NOT NULL
- `created_at` DATETIME(6) NOT NULL
- **CHECK**: `allocated_amount > 0`
- **UNIQUE**: `(id, repayment_schedule_id)` (For strict refund schedule mapping)
- **UNIQUE**: `(payment_id, repayment_schedule_id)`
- **FK**: `(payment_id, loan_id)` references `payments(id, loan_id)`
- **FK**: `(repayment_schedule_id, loan_id)` references `repayment_schedules(id, loan_id)`

### `refund_allocations`
- `id` BIGINT AUTO_INCREMENT PK
- `refund_payment_id` BIGINT NOT NULL
- `original_allocation_id` BIGINT NOT NULL
- `repayment_schedule_id` BIGINT NOT NULL
- `loan_id` BIGINT NOT NULL
- `refunded_amount` DECIMAL(19,4) NOT NULL
- `created_at` DATETIME(6) NOT NULL
- **CHECK**: `refunded_amount > 0`
- **UNIQUE**: `(refund_payment_id, original_allocation_id)`
- **FK**: `(refund_payment_id, loan_id)` references `payments(id, loan_id)`
- **FK**: `(original_allocation_id, repayment_schedule_id)` references `payment_allocations(id, repayment_schedule_id)`
- **FK**: `(repayment_schedule_id, loan_id)` references `repayment_schedules(id, loan_id)`
- **INDEX**: `idx_ra_schedule (repayment_schedule_id)`

## 9. Notification & Audit & Outbox Module
### `notifications`
- `id` BIGINT AUTO_INCREMENT PK
- `recipient_id` BIGINT NOT NULL FK (`users.id`)
- `type` VARCHAR(50) NOT NULL
- `subject` VARCHAR(255) NOT NULL
- `content` TEXT NOT NULL
- `status` VARCHAR(50) NOT NULL
- `read_at` DATETIME(6) NULL
- `created_at` DATETIME(6) NOT NULL

### `audit_logs`
- `id` BIGINT AUTO_INCREMENT PK
- `actor_id` BIGINT NULL FK (`users.id`)
- `action` VARCHAR(100) NOT NULL
- `entity_type` VARCHAR(100) NOT NULL
- `entity_id` BIGINT NOT NULL
- `old_value` JSON NULL
- `new_value` JSON NULL
- `ip_address` VARCHAR(45) NULL
- `created_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_audit_entity (entity_type, entity_id)`

### `outbox_events`
- `id` BIGINT AUTO_INCREMENT PK
- `aggregate_type` VARCHAR(100) NOT NULL
- `aggregate_id` BIGINT NOT NULL
- `type` VARCHAR(100) NOT NULL
- `payload` JSON NOT NULL
- `status` VARCHAR(50) NOT NULL (PENDING, IN_PROGRESS, PROCESSED, FAILED)
- `retry_count` INT NOT NULL DEFAULT 0
- `leased_by` VARCHAR(255) NULL
- `leased_until` DATETIME(6) NULL
- `next_attempt_at` DATETIME(6) NULL
- `processed_at` DATETIME(6) NULL
- `failure_details` TEXT NULL
- `created_at` DATETIME(6) NOT NULL
- **INDEX**: `idx_outbox_polling (status, next_attempt_at)`

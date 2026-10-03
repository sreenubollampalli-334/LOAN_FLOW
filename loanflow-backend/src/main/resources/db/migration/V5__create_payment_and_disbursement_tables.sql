CREATE TABLE disbursements (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL UNIQUE,
    amount DECIMAL(19,4) NOT NULL,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    disbursed_at DATETIME(6) NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_disb_amount CHECK (amount > 0),
    CONSTRAINT fk_disb_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE disbursement_attempts (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    disbursement_id BIGINT NOT NULL,
    attempt_number INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    failure_reason TEXT NULL,
    started_at DATETIME(6) NOT NULL,
    completed_at DATETIME(6) NULL,
    UNIQUE INDEX unq_da_disb_attempt (disbursement_id, attempt_number),
    CONSTRAINT fk_da_disbursement FOREIGN KEY (disbursement_id) REFERENCES disbursements (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE payments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    type VARCHAR(50) NOT NULL DEFAULT 'PAYMENT',
    original_payment_id BIGINT NULL,
    amount DECIMAL(19,4) NOT NULL,
    unapplied_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    refunded_unapplied_amount DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    payment_date DATETIME(6) NOT NULL,
    idempotency_key VARCHAR(100) NOT NULL,
    request_hash CHAR(64) NOT NULL,
    reference_number VARCHAR(100) NOT NULL UNIQUE,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_pay_amount CHECK (amount > 0),
    CONSTRAINT chk_pay_unapplied CHECK (unapplied_amount >= 0 AND unapplied_amount <= amount),
    CONSTRAINT chk_pay_refunded_unapplied CHECK (refunded_unapplied_amount >= 0 AND refunded_unapplied_amount <= unapplied_amount),
    UNIQUE INDEX unq_pay_cust_idempotency (customer_id, idempotency_key),
    UNIQUE INDEX unq_pay_id_loan (id, loan_id),
    INDEX idx_pay_loan_status (loan_id, status),
    CONSTRAINT fk_pay_loan_cust FOREIGN KEY (loan_id, customer_id) REFERENCES loans (id, customer_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pay_original FOREIGN KEY (original_payment_id) REFERENCES payments (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE payment_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    payment_id BIGINT NOT NULL,
    repayment_schedule_id BIGINT NOT NULL,
    loan_id BIGINT NOT NULL,
    allocated_amount DECIMAL(19,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_pa_amount CHECK (allocated_amount > 0),
    UNIQUE INDEX unq_pa_id_schedule (id, repayment_schedule_id),
    UNIQUE INDEX unq_pa_pay_schedule (payment_id, repayment_schedule_id),
    CONSTRAINT fk_pa_payment FOREIGN KEY (payment_id, loan_id) REFERENCES payments (id, loan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_pa_schedule FOREIGN KEY (repayment_schedule_id, loan_id) REFERENCES repayment_schedules (id, loan_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE refund_allocations (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    refund_payment_id BIGINT NOT NULL,
    original_allocation_id BIGINT NOT NULL,
    repayment_schedule_id BIGINT NOT NULL,
    loan_id BIGINT NOT NULL,
    refunded_amount DECIMAL(19,4) NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_ra_amount CHECK (refunded_amount > 0),
    UNIQUE INDEX unq_ra_refund_alloc (refund_payment_id, original_allocation_id),
    INDEX idx_ra_schedule (repayment_schedule_id),
    CONSTRAINT fk_ra_refund FOREIGN KEY (refund_payment_id, loan_id) REFERENCES payments (id, loan_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ra_original FOREIGN KEY (original_allocation_id, repayment_schedule_id) REFERENCES payment_allocations (id, repayment_schedule_id) ON DELETE RESTRICT,
    CONSTRAINT fk_ra_schedule FOREIGN KEY (repayment_schedule_id, loan_id) REFERENCES repayment_schedules (id, loan_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

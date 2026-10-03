CREATE TABLE credit_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    credit_officer_id BIGINT NOT NULL,
    decision VARCHAR(50) NOT NULL,
    comments TEXT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_cr_application FOREIGN KEY (application_id) REFERENCES loan_applications (id) ON DELETE RESTRICT,
    CONSTRAINT fk_cr_officer FOREIGN KEY (credit_officer_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_cr_app_reviewed (application_id, reviewed_at DESC, id DESC)
) ENGINE=InnoDB;

CREATE TABLE manager_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    manager_id BIGINT NOT NULL,
    decision VARCHAR(50) NOT NULL,
    comments TEXT NOT NULL,
    version INT NOT NULL DEFAULT 0,
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_mr_application FOREIGN KEY (application_id) REFERENCES loan_applications (id) ON DELETE RESTRICT,
    CONSTRAINT fk_mr_manager FOREIGN KEY (manager_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_mr_app_reviewed (application_id, reviewed_at DESC, id DESC)
) ENGINE=InnoDB;

CREATE TABLE loans (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL UNIQUE,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    principal_amount DECIMAL(19,4) NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    interest_calculation_method VARCHAR(50) NOT NULL,
    term_months INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_loan_principal CHECK (principal_amount > 0 AND term_months > 0),
    CONSTRAINT chk_loan_rate CHECK (interest_rate >= 0.0000 AND interest_rate <= 1.0000),
    UNIQUE INDEX unq_loan_id_customer (id, customer_id),
    CONSTRAINT fk_loan_app_cust_prod FOREIGN KEY (application_id, customer_id, product_id) REFERENCES loan_applications (id, customer_id, product_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE repayment_schedules (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    loan_id BIGINT NOT NULL,
    installment_number INT NOT NULL,
    due_date DATE NOT NULL,
    principal_amount DECIMAL(19,4) NOT NULL,
    interest_amount DECIMAL(19,4) NOT NULL,
    total_amount DECIMAL(19,4) NOT NULL,
    amount_paid DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    amount_refunded DECIMAL(19,4) NOT NULL DEFAULT 0.0000,
    status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    CONSTRAINT chk_rs_amounts CHECK (principal_amount >= 0 AND interest_amount >= 0),
    CONSTRAINT chk_rs_total CHECK (total_amount = principal_amount + interest_amount),
    CONSTRAINT chk_rs_paid CHECK (amount_paid >= 0 AND amount_paid <= total_amount),
    CONSTRAINT chk_rs_refunded CHECK (amount_refunded >= 0 AND amount_refunded <= amount_paid),
    UNIQUE INDEX unq_rs_loan_installment (loan_id, installment_number),
    UNIQUE INDEX unq_rs_id_loan (id, loan_id),
    INDEX idx_rs_loan_status (loan_id, status),
    CONSTRAINT fk_rs_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

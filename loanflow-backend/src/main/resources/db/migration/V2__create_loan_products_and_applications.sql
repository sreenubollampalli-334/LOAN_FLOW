CREATE TABLE loan_products (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    description TEXT NULL,
    min_amount DECIMAL(19,4) NOT NULL,
    max_amount DECIMAL(19,4) NOT NULL,
    interest_rate DECIMAL(5,4) NOT NULL,
    min_term_months INT NOT NULL,
    max_term_months INT NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_lp_amount CHECK (min_amount > 0 AND max_amount >= min_amount),
    CONSTRAINT chk_lp_rate CHECK (interest_rate >= 0.0000 AND interest_rate <= 1.0000),
    CONSTRAINT chk_lp_term CHECK (min_term_months > 0 AND max_term_months >= min_term_months)
) ENGINE=InnoDB;

CREATE TABLE product_required_documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    product_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    is_mandatory BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT fk_prd_product FOREIGN KEY (product_id) REFERENCES loan_products (id) ON DELETE RESTRICT,
    UNIQUE INDEX unq_prd_product_doc (product_id, document_type)
) ENGINE=InnoDB;

CREATE TABLE loan_applications (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    customer_id BIGINT NOT NULL,
    product_id BIGINT NOT NULL,
    requested_amount DECIMAL(19,4) NOT NULL,
    approved_amount DECIMAL(19,4) NULL,
    term_months INT NOT NULL,
    status VARCHAR(50) NOT NULL,
    purpose TEXT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_la_customer FOREIGN KEY (customer_id) REFERENCES customer_profiles (id) ON DELETE RESTRICT,
    CONSTRAINT fk_la_product FOREIGN KEY (product_id) REFERENCES loan_products (id) ON DELETE RESTRICT,
    CONSTRAINT chk_la_req_amount CHECK (requested_amount > 0 AND term_months > 0),
    CONSTRAINT chk_la_app_amount CHECK (approved_amount IS NULL OR (approved_amount > 0 AND approved_amount <= requested_amount)),
    UNIQUE INDEX unq_la_id_customer (id, customer_id),
    UNIQUE INDEX unq_la_id_customer_product (id, customer_id, product_id),
    INDEX idx_la_status (status)
) ENGINE=InnoDB;

CREATE TABLE application_status_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    previous_status VARCHAR(50) NULL,
    new_status VARCHAR(50) NOT NULL,
    reason TEXT NULL,
    actor_id BIGINT NOT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ash_application FOREIGN KEY (application_id) REFERENCES loan_applications (id) ON DELETE RESTRICT,
    CONSTRAINT fk_ash_actor FOREIGN KEY (actor_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_ash_app_created (application_id, created_at DESC)
) ENGINE=InnoDB;

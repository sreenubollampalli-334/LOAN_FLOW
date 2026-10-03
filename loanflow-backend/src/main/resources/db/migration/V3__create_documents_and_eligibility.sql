CREATE TABLE documents (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    customer_id BIGINT NOT NULL,
    document_type VARCHAR(50) NOT NULL,
    original_filename VARCHAR(255) NOT NULL,
    content_type VARCHAR(100) NOT NULL,
    file_size BIGINT NOT NULL,
    checksum VARCHAR(255) NOT NULL,
    storage_key VARCHAR(512) NOT NULL UNIQUE,
    upload_status VARCHAR(50) NOT NULL,
    version INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT chk_doc_file_size CHECK (file_size > 0),
    CONSTRAINT fk_doc_app_cust FOREIGN KEY (application_id, customer_id) REFERENCES loan_applications (id, customer_id) ON DELETE RESTRICT
) ENGINE=InnoDB;

CREATE TABLE document_reviews (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    document_id BIGINT NOT NULL,
    reviewer_id BIGINT NOT NULL,
    status VARCHAR(50) NOT NULL,
    comments TEXT NULL,
    reviewed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_dr_document FOREIGN KEY (document_id) REFERENCES documents (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dr_reviewer FOREIGN KEY (reviewer_id) REFERENCES users (id) ON DELETE RESTRICT,
    INDEX idx_dr_doc_reviewed (document_id, reviewed_at DESC)
) ENGINE=InnoDB;

CREATE TABLE eligibility_assessments (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    application_id BIGINT NOT NULL,
    ruleset_version VARCHAR(50) NOT NULL,
    decision VARCHAR(20) NOT NULL,
    input_snapshot JSON NOT NULL,
    assessed_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_ea_application FOREIGN KEY (application_id) REFERENCES loan_applications (id) ON DELETE RESTRICT,
    INDEX idx_ea_app_assessed (application_id, assessed_at DESC)
) ENGINE=InnoDB;

CREATE TABLE eligibility_reason_codes (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    assessment_id BIGINT NOT NULL,
    code VARCHAR(50) NOT NULL,
    description TEXT NOT NULL,
    CONSTRAINT fk_erc_assessment FOREIGN KEY (assessment_id) REFERENCES eligibility_assessments (id) ON DELETE RESTRICT
) ENGINE=InnoDB;

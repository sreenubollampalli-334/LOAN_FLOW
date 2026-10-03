CREATE TABLE disbursement_idempotency_keys (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL UNIQUE,
    request_hash CHAR(64) NOT NULL,
    loan_id BIGINT NOT NULL,
    disbursement_id BIGINT NULL,
    response_payload TEXT NULL,
    created_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_dik_loan FOREIGN KEY (loan_id) REFERENCES loans (id) ON DELETE RESTRICT,
    CONSTRAINT fk_dik_disbursement FOREIGN KEY (disbursement_id) REFERENCES disbursements (id) ON DELETE SET NULL
) ENGINE=InnoDB;

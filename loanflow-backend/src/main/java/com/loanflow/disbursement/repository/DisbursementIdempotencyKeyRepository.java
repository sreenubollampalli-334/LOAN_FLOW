package com.loanflow.disbursement.repository;

import com.loanflow.disbursement.entity.DisbursementIdempotencyKey;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface DisbursementIdempotencyKeyRepository extends JpaRepository<DisbursementIdempotencyKey, Long> {
    Optional<DisbursementIdempotencyKey> findByIdempotencyKey(String idempotencyKey);
}

package com.loanflow.disbursement.repository;

import com.loanflow.disbursement.entity.DisbursementAttempt;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DisbursementAttemptRepository extends JpaRepository<DisbursementAttempt, Long> {
    List<DisbursementAttempt> findByDisbursementIdOrderByAttemptNumberAsc(Long disbursementId);
}

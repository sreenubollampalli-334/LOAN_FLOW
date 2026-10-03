package com.loanflow.loan.repository;

import com.loanflow.loan.entity.LoanApplication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanApplicationRepository extends JpaRepository<LoanApplication, Long> {
    List<LoanApplication> findByCustomerId(Long customerId);
    List<LoanApplication> findByProductId(Long productId);
    List<LoanApplication> findByStatus(String status);
    Optional<LoanApplication> findByIdAndCustomerId(Long id, Long customerId);
    Page<LoanApplication> findByCustomerId(Long customerId, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LoanApplication l WHERE l.id = :id")
    Optional<LoanApplication> findByIdForUpdate(@Param("id") Long id);
}

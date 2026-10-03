package com.loanflow.payment.repository;

import com.loanflow.payment.entity.Payment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface PaymentRepository extends JpaRepository<Payment, Long> {
    List<Payment> findByLoanId(Long loanId);
    Optional<Payment> findByReferenceNumber(String referenceNumber);
    Optional<Payment> findByCustomerIdAndIdempotencyKey(Long customerId, String idempotencyKey);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT p FROM Payment p WHERE p.id = :id")
    Optional<Payment> findByIdWithLock(@Param("id") Long id);

    @Query("SELECT p FROM Payment p WHERE p.customer.id = :customerId " +
           "AND (:loanId IS NULL OR p.loan.id = :loanId) " +
           "AND (:status IS NULL OR p.status = :status)")
    org.springframework.data.domain.Page<Payment> findPaymentsByCustomer(
            @Param("customerId") Long customerId, 
            @Param("loanId") Long loanId, 
            @Param("status") String status, 
            org.springframework.data.domain.Pageable pageable);

    org.springframework.data.domain.Page<Payment> findByLoanId(Long loanId, org.springframework.data.domain.Pageable pageable);

    List<Payment> findByOriginalPaymentId(Long originalPaymentId);
}

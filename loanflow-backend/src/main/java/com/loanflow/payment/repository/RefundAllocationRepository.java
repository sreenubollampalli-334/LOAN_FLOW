package com.loanflow.payment.repository;

import com.loanflow.payment.entity.RefundAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface RefundAllocationRepository extends JpaRepository<RefundAllocation, Long> {
    List<RefundAllocation> findByRefundPaymentId(Long refundPaymentId);
    List<RefundAllocation> findByOriginalAllocationId(Long originalAllocationId);
    List<RefundAllocation> findByLoanId(Long loanId);
}

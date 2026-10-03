package com.loanflow.payment.repository;

import com.loanflow.payment.entity.PaymentAllocation;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PaymentAllocationRepository extends JpaRepository<PaymentAllocation, Long> {
    List<PaymentAllocation> findByPaymentId(Long paymentId);
    List<PaymentAllocation> findByRepaymentScheduleId(Long repaymentScheduleId);
    List<PaymentAllocation> findByLoanId(Long loanId);
}

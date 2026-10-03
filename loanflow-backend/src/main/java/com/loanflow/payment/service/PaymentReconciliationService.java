package com.loanflow.payment.service;

import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.payment.dto.PaymentReconciliationResponse;
import com.loanflow.payment.entity.Payment;
import com.loanflow.payment.entity.PaymentAllocation;
import com.loanflow.payment.entity.RefundAllocation;
import com.loanflow.payment.repository.PaymentAllocationRepository;
import com.loanflow.payment.repository.PaymentRepository;
import com.loanflow.payment.repository.RefundAllocationRepository;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class PaymentReconciliationService {

    private final LoanRepository loanRepository;
    private final PaymentRepository paymentRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final RefundAllocationRepository refundAllocationRepository;

    public PaymentReconciliationService(LoanRepository loanRepository,
                                        PaymentRepository paymentRepository,
                                        RepaymentScheduleRepository repaymentScheduleRepository,
                                        PaymentAllocationRepository paymentAllocationRepository,
                                        RefundAllocationRepository refundAllocationRepository) {
        this.loanRepository = loanRepository;
        this.paymentRepository = paymentRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.refundAllocationRepository = refundAllocationRepository;
    }

    @Transactional(readOnly = true)
    public PaymentReconciliationResponse reconcileLoan(Long loanId) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        List<Payment> payments = paymentRepository.findByLoanId(loanId);
        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loanId);
        List<PaymentAllocation> pAllocations = paymentAllocationRepository.findByLoanId(loanId);
        List<RefundAllocation> rAllocations = refundAllocationRepository.findByLoanId(loanId);

        List<String> inconsistencies = new ArrayList<>();

        BigDecimal totalPayments = BigDecimal.ZERO;
        BigDecimal totalAllocated = BigDecimal.ZERO;
        BigDecimal totalUnapplied = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        Map<Long, List<PaymentAllocation>> pAllocByPaymentId = pAllocations.stream()
                .collect(Collectors.groupingBy(pa -> pa.getPayment().getId()));
        
        Map<Long, List<RefundAllocation>> rAllocByRefundId = rAllocations.stream()
                .collect(Collectors.groupingBy(ra -> ra.getRefundPayment().getId()));

        Map<Long, List<RefundAllocation>> rAllocByOriginalAllocId = rAllocations.stream()
                .collect(Collectors.groupingBy(ra -> ra.getOriginalAllocation().getId()));

        // 1. Check Payments
        for (Payment payment : payments) {
            if ("PAYMENT".equals(payment.getType())) {
                totalPayments = totalPayments.add(payment.getAmount());
                totalUnapplied = totalUnapplied.add(payment.getUnappliedAmount());
                
                List<PaymentAllocation> allocs = pAllocByPaymentId.getOrDefault(payment.getId(), new ArrayList<>());
                BigDecimal sumAllocated = allocs.stream()
                        .map(PaymentAllocation::getAllocatedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);
                totalAllocated = totalAllocated.add(sumAllocated);

                BigDecimal expectedAmount = payment.getUnappliedAmount().add(sumAllocated);
                if (payment.getAmount().compareTo(expectedAmount) != 0) {
                    inconsistencies.add("Payment ID " + payment.getId() + " accounting amount (" + payment.getAmount() + ") does not equal unapplied (" + payment.getUnappliedAmount() + ") + allocations (" + sumAllocated + ")");
                }
            } else if ("REFUND".equals(payment.getType())) {
                totalRefunded = totalRefunded.add(payment.getAmount());

                List<RefundAllocation> rAllocs = rAllocByRefundId.getOrDefault(payment.getId(), new ArrayList<>());
                BigDecimal sumRefundAllocated = rAllocs.stream()
                        .map(RefundAllocation::getRefundedAmount)
                        .reduce(BigDecimal.ZERO, BigDecimal::add);

                // For refunds, the amount is the refunded unapplied amount on the original payment + the refund allocations
                // Wait, refund payment entity doesn't have refundedUnappliedAmount, the original payment does!
                // But the refund payment represents a total amount. Is it equal to the sum of refund allocations + refundedUnappliedAmount *for this refund*?
                // Our implementation increases the original payment's refundedUnappliedAmount. It doesn't track it per refund payment!
                // Let's check original payment.
            }
        }

        // 2. Check original payments for over-refunds
        for (Payment payment : payments) {
            if ("PAYMENT".equals(payment.getType())) {
                BigDecimal refundableUnapplied = payment.getUnappliedAmount().subtract(payment.getRefundedUnappliedAmount());
                // Actually the refundable is: (Original Unapplied + original allocations) - (already refunded).
                // But specifically: originalPayment.getRefundedUnappliedAmount() <= originalPayment.getUnappliedAmount()
                // wait, during allocation, unappliedAmount is decreased. So unappliedAmount is the CURRENT unapplied.
                // If we refund from unapplied, we increase refundedUnappliedAmount. So refundedUnappliedAmount should not exceed unappliedAmount.
                // Wait, in PaymentService: refundableUnapplied = originalPayment.getUnappliedAmount().subtract(originalPayment.getRefundedUnappliedAmount());
                // So refundedUnappliedAmount cannot exceed unappliedAmount!
                if (payment.getRefundedUnappliedAmount().compareTo(payment.getUnappliedAmount()) > 0) {
                    inconsistencies.add("Payment ID " + payment.getId() + " refunded unapplied amount (" + payment.getRefundedUnappliedAmount() + ") exceeds unapplied amount (" + payment.getUnappliedAmount() + ")");
                }
            }
        }

        // 3. Check Allocations over-refund
        for (PaymentAllocation pa : pAllocations) {
            List<RefundAllocation> rAllocs = rAllocByOriginalAllocId.getOrDefault(pa.getId(), new ArrayList<>());
            BigDecimal totalRefundForAlloc = rAllocs.stream()
                    .map(RefundAllocation::getRefundedAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            if (totalRefundForAlloc.compareTo(pa.getAllocatedAmount()) > 0) {
                inconsistencies.add("PaymentAllocation ID " + pa.getId() + " refunded amount (" + totalRefundForAlloc + ") exceeds original allocation (" + pa.getAllocatedAmount() + ")");
            }
        }

        // 4. Check Repayment Schedules
        for (RepaymentSchedule rs : schedules) {
            BigDecimal netOutstanding = rs.getTotalAmount().subtract(rs.getAmountPaid()).add(rs.getAmountRefunded());
            totalOutstanding = totalOutstanding.add(netOutstanding);

            if (netOutstanding.compareTo(BigDecimal.ZERO) < 0) {
                inconsistencies.add("RepaymentSchedule ID " + rs.getId() + " has negative outstanding balance (" + netOutstanding + ")");
            }
            if (rs.getAmountPaid().compareTo(rs.getTotalAmount()) > 0) {
                inconsistencies.add("RepaymentSchedule ID " + rs.getId() + " amountPaid (" + rs.getAmountPaid() + ") exceeds totalAmount (" + rs.getTotalAmount() + ")");
            }
            if (rs.getAmountRefunded().compareTo(rs.getAmountPaid()) > 0) {
                inconsistencies.add("RepaymentSchedule ID " + rs.getId() + " amountRefunded (" + rs.getAmountRefunded() + ") exceeds amountPaid (" + rs.getAmountPaid() + ")");
            }
        }

        PaymentReconciliationResponse response = new PaymentReconciliationResponse();
        response.setLoanId(loanId);
        response.setIsConsistent(inconsistencies.isEmpty());
        response.setTotalPayments(totalPayments);
        response.setTotalAllocated(totalAllocated);
        response.setTotalUnapplied(totalUnapplied);
        response.setTotalRefunded(totalRefunded);
        response.setTotalOutstanding(totalOutstanding);
        response.setInconsistencies(inconsistencies);

        return response;
    }
}

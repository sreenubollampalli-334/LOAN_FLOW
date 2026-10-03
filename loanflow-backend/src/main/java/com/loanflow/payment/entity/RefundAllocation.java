package com.loanflow.payment.entity;

import com.loanflow.loan.entity.Loan;
import com.loanflow.repayment.entity.RepaymentSchedule;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "refund_allocations", 
    uniqueConstraints = {
        @UniqueConstraint(name = "unq_ra_refund_alloc", columnNames = {"refund_payment_id", "original_allocation_id"})
    },
    indexes = {
        @Index(name = "idx_ra_schedule", columnList = "repayment_schedule_id")
    }
)
public class RefundAllocation {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "refund_payment_id", nullable = false)
    private Payment refundPayment;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "original_allocation_id", nullable = false)
    private PaymentAllocation originalAllocation;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "repayment_schedule_id", nullable = false)
    private RepaymentSchedule repaymentSchedule;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "loan_id", nullable = false)
    private Loan loan;

    @Column(name = "refunded_amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal refundedAmount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }

    public RefundAllocation() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Payment getRefundPayment() { return refundPayment; }
    public void setRefundPayment(Payment refundPayment) { this.refundPayment = refundPayment; }
    public PaymentAllocation getOriginalAllocation() { return originalAllocation; }
    public void setOriginalAllocation(PaymentAllocation originalAllocation) { this.originalAllocation = originalAllocation; }
    public RepaymentSchedule getRepaymentSchedule() { return repaymentSchedule; }
    public void setRepaymentSchedule(RepaymentSchedule repaymentSchedule) { this.repaymentSchedule = repaymentSchedule; }
    public Loan getLoan() { return loan; }
    public void setLoan(Loan loan) { this.loan = loan; }
    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

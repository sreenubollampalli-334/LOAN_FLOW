package com.loanflow.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class RefundAllocationDto {
    private Long id;
    private Long originalAllocationId;
    private Long repaymentScheduleId;
    private BigDecimal refundedAmount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getOriginalAllocationId() { return originalAllocationId; }
    public void setOriginalAllocationId(Long originalAllocationId) { this.originalAllocationId = originalAllocationId; }

    public Long getRepaymentScheduleId() { return repaymentScheduleId; }
    public void setRepaymentScheduleId(Long repaymentScheduleId) { this.repaymentScheduleId = repaymentScheduleId; }

    public BigDecimal getRefundedAmount() { return refundedAmount; }
    public void setRefundedAmount(BigDecimal refundedAmount) { this.refundedAmount = refundedAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

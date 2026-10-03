package com.loanflow.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class PaymentAllocationDto {
    private Long id;
    private Long repaymentScheduleId;
    private BigDecimal allocatedAmount;
    private LocalDateTime createdAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRepaymentScheduleId() { return repaymentScheduleId; }
    public void setRepaymentScheduleId(Long repaymentScheduleId) { this.repaymentScheduleId = repaymentScheduleId; }

    public BigDecimal getAllocatedAmount() { return allocatedAmount; }
    public void setAllocatedAmount(BigDecimal allocatedAmount) { this.allocatedAmount = allocatedAmount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}

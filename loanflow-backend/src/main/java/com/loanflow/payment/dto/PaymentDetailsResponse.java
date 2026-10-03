package com.loanflow.payment.dto;

import java.math.BigDecimal;
import java.util.List;

public class PaymentDetailsResponse extends PaymentResponse {
    private BigDecimal unappliedAmount;
    private BigDecimal refundedUnappliedAmount;
    private List<PaymentAllocationDto> allocations;

    public BigDecimal getUnappliedAmount() { return unappliedAmount; }
    public void setUnappliedAmount(BigDecimal unappliedAmount) { this.unappliedAmount = unappliedAmount; }

    public BigDecimal getRefundedUnappliedAmount() { return refundedUnappliedAmount; }
    public void setRefundedUnappliedAmount(BigDecimal refundedUnappliedAmount) { this.refundedUnappliedAmount = refundedUnappliedAmount; }

    public List<PaymentAllocationDto> getAllocations() { return allocations; }
    public void setAllocations(List<PaymentAllocationDto> allocations) { this.allocations = allocations; }
}

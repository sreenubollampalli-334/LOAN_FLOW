package com.loanflow.payment.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class RefundHistoryResponse {
    private Long id;
    private BigDecimal amount;
    private String status;
    private LocalDateTime paymentDate;
    private String referenceNumber;
    private List<RefundAllocationDto> refundAllocations;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getPaymentDate() { return paymentDate; }
    public void setPaymentDate(LocalDateTime paymentDate) { this.paymentDate = paymentDate; }

    public String getReferenceNumber() { return referenceNumber; }
    public void setReferenceNumber(String referenceNumber) { this.referenceNumber = referenceNumber; }

    public List<RefundAllocationDto> getRefundAllocations() { return refundAllocations; }
    public void setRefundAllocations(List<RefundAllocationDto> refundAllocations) { this.refundAllocations = refundAllocations; }
}

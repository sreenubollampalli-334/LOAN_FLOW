package com.loanflow.payment.dto;

import java.math.BigDecimal;
import java.util.List;

public class PaymentReconciliationResponse {
    private Long loanId;
    private boolean isConsistent;
    private BigDecimal totalPayments;
    private BigDecimal totalAllocated;
    private BigDecimal totalUnapplied;
    private BigDecimal totalRefunded;
    private BigDecimal totalOutstanding;
    private List<String> inconsistencies;

    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }

    public boolean getIsConsistent() { return isConsistent; }
    public void setIsConsistent(boolean isConsistent) { this.isConsistent = isConsistent; }

    public BigDecimal getTotalPayments() { return totalPayments; }
    public void setTotalPayments(BigDecimal totalPayments) { this.totalPayments = totalPayments; }

    public BigDecimal getTotalAllocated() { return totalAllocated; }
    public void setTotalAllocated(BigDecimal totalAllocated) { this.totalAllocated = totalAllocated; }

    public BigDecimal getTotalUnapplied() { return totalUnapplied; }
    public void setTotalUnapplied(BigDecimal totalUnapplied) { this.totalUnapplied = totalUnapplied; }

    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public void setTotalRefunded(BigDecimal totalRefunded) { this.totalRefunded = totalRefunded; }

    public BigDecimal getTotalOutstanding() { return totalOutstanding; }
    public void setTotalOutstanding(BigDecimal totalOutstanding) { this.totalOutstanding = totalOutstanding; }

    public List<String> getInconsistencies() { return inconsistencies; }
    public void setInconsistencies(List<String> inconsistencies) { this.inconsistencies = inconsistencies; }
}

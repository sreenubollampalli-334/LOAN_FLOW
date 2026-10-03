package com.loanflow.repayment.dto;

import java.math.BigDecimal;

public class RepaymentSummaryResponse {

    private Long loanId;
    private BigDecimal totalScheduledAmount;
    private BigDecimal totalPaid;
    private BigDecimal totalRefunded;
    private BigDecimal outstandingBalance;
    private int pendingInstallments;
    private int paidInstallments;

    public RepaymentSummaryResponse() {}

    public RepaymentSummaryResponse(Long loanId, BigDecimal totalScheduledAmount, BigDecimal totalPaid,
                                    BigDecimal totalRefunded, BigDecimal outstandingBalance,
                                    int pendingInstallments, int paidInstallments) {
        this.loanId = loanId;
        this.totalScheduledAmount = totalScheduledAmount;
        this.totalPaid = totalPaid;
        this.totalRefunded = totalRefunded;
        this.outstandingBalance = outstandingBalance;
        this.pendingInstallments = pendingInstallments;
        this.paidInstallments = paidInstallments;
    }

    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }

    public BigDecimal getTotalScheduledAmount() { return totalScheduledAmount; }
    public void setTotalScheduledAmount(BigDecimal totalScheduledAmount) { this.totalScheduledAmount = totalScheduledAmount; }

    public BigDecimal getTotalPaid() { return totalPaid; }
    public void setTotalPaid(BigDecimal totalPaid) { this.totalPaid = totalPaid; }

    public BigDecimal getTotalRefunded() { return totalRefunded; }
    public void setTotalRefunded(BigDecimal totalRefunded) { this.totalRefunded = totalRefunded; }

    public BigDecimal getOutstandingBalance() { return outstandingBalance; }
    public void setOutstandingBalance(BigDecimal outstandingBalance) { this.outstandingBalance = outstandingBalance; }

    public int getPendingInstallments() { return pendingInstallments; }
    public void setPendingInstallments(int pendingInstallments) { this.pendingInstallments = pendingInstallments; }

    public int getPaidInstallments() { return paidInstallments; }
    public void setPaidInstallments(int paidInstallments) { this.paidInstallments = paidInstallments; }
}

package com.loanflow.repayment.dto;

import java.math.BigDecimal;
import java.util.List;

public class CustomerRepaymentOverviewResponse {

    private BigDecimal totalOutstandingBalance;
    private BigDecimal totalPaidAmount;
    private int totalActiveLoans;
    private List<RepaymentSummaryResponse> loanSummaries;

    public CustomerRepaymentOverviewResponse() {}

    public CustomerRepaymentOverviewResponse(BigDecimal totalOutstandingBalance, BigDecimal totalPaidAmount,
                                             int totalActiveLoans, List<RepaymentSummaryResponse> loanSummaries) {
        this.totalOutstandingBalance = totalOutstandingBalance;
        this.totalPaidAmount = totalPaidAmount;
        this.totalActiveLoans = totalActiveLoans;
        this.loanSummaries = loanSummaries;
    }

    public BigDecimal getTotalOutstandingBalance() { return totalOutstandingBalance; }
    public void setTotalOutstandingBalance(BigDecimal totalOutstandingBalance) { this.totalOutstandingBalance = totalOutstandingBalance; }

    public BigDecimal getTotalPaidAmount() { return totalPaidAmount; }
    public void setTotalPaidAmount(BigDecimal totalPaidAmount) { this.totalPaidAmount = totalPaidAmount; }

    public int getTotalActiveLoans() { return totalActiveLoans; }
    public void setTotalActiveLoans(int totalActiveLoans) { this.totalActiveLoans = totalActiveLoans; }

    public List<RepaymentSummaryResponse> getLoanSummaries() { return loanSummaries; }
    public void setLoanSummaries(List<RepaymentSummaryResponse> loanSummaries) { this.loanSummaries = loanSummaries; }
}

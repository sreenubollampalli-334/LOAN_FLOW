package com.loanflow.repayment.dto;

import java.math.BigDecimal;
import java.util.List;

public class EmiCalculationResponse {

    private BigDecimal principalAmount;
    private BigDecimal monthlyInstallment;
    private BigDecimal totalInterest;
    private BigDecimal totalRepayment;
    private Integer termMonths;
    private String interestCalculationMethod;
    private List<InstallmentBreakdownResponse> breakdown;

    public EmiCalculationResponse() {}

    public EmiCalculationResponse(BigDecimal principalAmount, BigDecimal monthlyInstallment, BigDecimal totalInterest, BigDecimal totalRepayment, Integer termMonths, String interestCalculationMethod, List<InstallmentBreakdownResponse> breakdown) {
        this.principalAmount = principalAmount;
        this.monthlyInstallment = monthlyInstallment;
        this.totalInterest = totalInterest;
        this.totalRepayment = totalRepayment;
        this.termMonths = termMonths;
        this.interestCalculationMethod = interestCalculationMethod;
        this.breakdown = breakdown;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getMonthlyInstallment() {
        return monthlyInstallment;
    }

    public void setMonthlyInstallment(BigDecimal monthlyInstallment) {
        this.monthlyInstallment = monthlyInstallment;
    }

    public BigDecimal getTotalInterest() {
        return totalInterest;
    }

    public void setTotalInterest(BigDecimal totalInterest) {
        this.totalInterest = totalInterest;
    }

    public BigDecimal getTotalRepayment() {
        return totalRepayment;
    }

    public void setTotalRepayment(BigDecimal totalRepayment) {
        this.totalRepayment = totalRepayment;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public String getInterestCalculationMethod() {
        return interestCalculationMethod;
    }

    public void setInterestCalculationMethod(String interestCalculationMethod) {
        this.interestCalculationMethod = interestCalculationMethod;
    }

    public List<InstallmentBreakdownResponse> getBreakdown() {
        return breakdown;
    }

    public void setBreakdown(List<InstallmentBreakdownResponse> breakdown) {
        this.breakdown = breakdown;
    }
}

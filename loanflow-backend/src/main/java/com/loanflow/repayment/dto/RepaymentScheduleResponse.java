package com.loanflow.repayment.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public class RepaymentScheduleResponse {

    private Long id;
    private Long loanId;
    private Integer installmentNumber;
    private LocalDate dueDate;
    private BigDecimal principalAmount;
    private BigDecimal interestAmount;
    private BigDecimal totalAmount;
    private BigDecimal amountPaid;
    private BigDecimal amountRefunded;
    private BigDecimal netOutstanding;
    private String status;

    public RepaymentScheduleResponse() {}

    public RepaymentScheduleResponse(Long id, Long loanId, Integer installmentNumber, LocalDate dueDate,
                                   BigDecimal principalAmount, BigDecimal interestAmount, BigDecimal totalAmount,
                                   BigDecimal amountPaid, BigDecimal amountRefunded, BigDecimal netOutstanding, String status) {
        this.id = id;
        this.loanId = loanId;
        this.installmentNumber = installmentNumber;
        this.dueDate = dueDate;
        this.principalAmount = principalAmount;
        this.interestAmount = interestAmount;
        this.totalAmount = totalAmount;
        this.amountPaid = amountPaid;
        this.amountRefunded = amountRefunded;
        this.netOutstanding = netOutstanding;
        this.status = status;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getLoanId() { return loanId; }
    public void setLoanId(Long loanId) { this.loanId = loanId; }

    public Integer getInstallmentNumber() { return installmentNumber; }
    public void setInstallmentNumber(Integer installmentNumber) { this.installmentNumber = installmentNumber; }

    public LocalDate getDueDate() { return dueDate; }
    public void setDueDate(LocalDate dueDate) { this.dueDate = dueDate; }

    public BigDecimal getPrincipalAmount() { return principalAmount; }
    public void setPrincipalAmount(BigDecimal principalAmount) { this.principalAmount = principalAmount; }

    public BigDecimal getInterestAmount() { return interestAmount; }
    public void setInterestAmount(BigDecimal interestAmount) { this.interestAmount = interestAmount; }

    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }

    public BigDecimal getAmountPaid() { return amountPaid; }
    public void setAmountPaid(BigDecimal amountPaid) { this.amountPaid = amountPaid; }

    public BigDecimal getAmountRefunded() { return amountRefunded; }
    public void setAmountRefunded(BigDecimal amountRefunded) { this.amountRefunded = amountRefunded; }

    public BigDecimal getNetOutstanding() { return netOutstanding; }
    public void setNetOutstanding(BigDecimal netOutstanding) { this.netOutstanding = netOutstanding; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}

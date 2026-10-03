package com.loanflow.disbursement.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class DisbursementResponse {
    private Long id;
    private Long loanId;
    private BigDecimal amount;
    private String status;
    private Boolean isDemo;
    private String demoDisclaimer;
    private LocalDateTime disbursedAt;
    private LocalDateTime createdAt;
    private Integer attemptsCount;

    public DisbursementResponse() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getLoanId() {
        return loanId;
    }

    public void setLoanId(Long loanId) {
        this.loanId = loanId;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getIsDemo() {
        return isDemo;
    }

    public void setIsDemo(Boolean isDemo) {
        this.isDemo = isDemo;
    }

    public String getDemoDisclaimer() {
        return demoDisclaimer;
    }

    public void setDemoDisclaimer(String demoDisclaimer) {
        this.demoDisclaimer = demoDisclaimer;
    }

    public LocalDateTime getDisbursedAt() {
        return disbursedAt;
    }

    public void setDisbursedAt(LocalDateTime disbursedAt) {
        this.disbursedAt = disbursedAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public Integer getAttemptsCount() {
        return attemptsCount;
    }

    public void setAttemptsCount(Integer attemptsCount) {
        this.attemptsCount = attemptsCount;
    }
}

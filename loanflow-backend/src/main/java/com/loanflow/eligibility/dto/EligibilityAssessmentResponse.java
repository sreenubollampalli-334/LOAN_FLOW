package com.loanflow.eligibility.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

public class EligibilityAssessmentResponse {
    private Long id;
    private Long applicationId;
    private String decision;
    private Boolean isDemo;
    private String demoDisclaimer;
    private String rulesetVersion;
    private LocalDateTime assessedAt;
    private List<ReasonCodeResponse> reasons;
    private BigDecimal requestedAmount;
    private Integer termMonths;
    private String productName;

    public EligibilityAssessmentResponse() {}

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
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

    public String getRulesetVersion() {
        return rulesetVersion;
    }

    public void setRulesetVersion(String rulesetVersion) {
        this.rulesetVersion = rulesetVersion;
    }

    public LocalDateTime getAssessedAt() {
        return assessedAt;
    }

    public void setAssessedAt(LocalDateTime assessedAt) {
        this.assessedAt = assessedAt;
    }

    public List<ReasonCodeResponse> getReasons() {
        return reasons;
    }

    public void setReasons(List<ReasonCodeResponse> reasons) {
        this.reasons = reasons;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }

    public void setRequestedAmount(BigDecimal requestedAmount) {
        this.requestedAmount = requestedAmount;
    }

    public Integer getTermMonths() {
        return termMonths;
    }

    public void setTermMonths(Integer termMonths) {
        this.termMonths = termMonths;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
    }
}

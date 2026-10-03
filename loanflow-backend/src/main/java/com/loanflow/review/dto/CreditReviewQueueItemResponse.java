package com.loanflow.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class CreditReviewQueueItemResponse {
    private Long applicationId;
    private String productName;
    private BigDecimal requestedAmount;
    private Integer termMonths;
    private LocalDateTime submittedAt;
    private String eligibilityDecision;
    private Boolean hasExistingReview;

    public CreditReviewQueueItemResponse() {}

    public Long getApplicationId() {
        return applicationId;
    }

    public void setApplicationId(Long applicationId) {
        this.applicationId = applicationId;
    }

    public String getProductName() {
        return productName;
    }

    public void setProductName(String productName) {
        this.productName = productName;
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

    public LocalDateTime getSubmittedAt() {
        return submittedAt;
    }

    public void setSubmittedAt(LocalDateTime submittedAt) {
        this.submittedAt = submittedAt;
    }

    public String getEligibilityDecision() {
        return eligibilityDecision;
    }

    public void setEligibilityDecision(String eligibilityDecision) {
        this.eligibilityDecision = eligibilityDecision;
    }

    public Boolean getHasExistingReview() {
        return hasExistingReview;
    }

    public void setHasExistingReview(Boolean hasExistingReview) {
        this.hasExistingReview = hasExistingReview;
    }
}

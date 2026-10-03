package com.loanflow.review.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public class ManagerReviewQueueItemResponse {
    private Long applicationId;
    private String productName;
    private BigDecimal requestedAmount;
    private Integer termMonths;
    private LocalDateTime submittedAt;
    private String eligibilityDecision;
    private String creditOfficerRecommendation;
    private String creditOfficerComments;
    private Boolean hasExistingManagerReview;

    public ManagerReviewQueueItemResponse() {}

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

    public String getCreditOfficerRecommendation() {
        return creditOfficerRecommendation;
    }

    public void setCreditOfficerRecommendation(String creditOfficerRecommendation) {
        this.creditOfficerRecommendation = creditOfficerRecommendation;
    }

    public String getCreditOfficerComments() {
        return creditOfficerComments;
    }

    public void setCreditOfficerComments(String creditOfficerComments) {
        this.creditOfficerComments = creditOfficerComments;
    }

    public Boolean getHasExistingManagerReview() {
        return hasExistingManagerReview;
    }

    public void setHasExistingManagerReview(Boolean hasExistingManagerReview) {
        this.hasExistingManagerReview = hasExistingManagerReview;
    }
}

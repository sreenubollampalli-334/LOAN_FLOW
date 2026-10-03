package com.loanflow.review.dto;

import java.time.LocalDateTime;

public class CreditReviewResponse {
    private Long id;
    private Long applicationId;
    private Long creditOfficerId;
    private String creditOfficerEmail;
    private String decision;
    private String comments;
    private LocalDateTime reviewedAt;

    public CreditReviewResponse() {}

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

    public Long getCreditOfficerId() {
        return creditOfficerId;
    }

    public void setCreditOfficerId(Long creditOfficerId) {
        this.creditOfficerId = creditOfficerId;
    }

    public String getCreditOfficerEmail() {
        return creditOfficerEmail;
    }

    public void setCreditOfficerEmail(String creditOfficerEmail) {
        this.creditOfficerEmail = creditOfficerEmail;
    }

    public String getDecision() {
        return decision;
    }

    public void setDecision(String decision) {
        this.decision = decision;
    }

    public String getComments() {
        return comments;
    }

    public void setComments(String comments) {
        this.comments = comments;
    }

    public LocalDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(LocalDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }
}

package com.loanflow.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public class DocumentReviewRequest {

    @NotBlank(message = "Status is required")
    @Pattern(regexp = "VERIFIED|REJECTED", message = "Status must be VERIFIED or REJECTED")
    private String status;

    private String comments;

    public DocumentReviewRequest() {}

    public DocumentReviewRequest(String status, String comments) {
        this.status = status;
        this.comments = comments;
    }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
}

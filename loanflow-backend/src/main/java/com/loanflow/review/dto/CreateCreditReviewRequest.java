package com.loanflow.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateCreditReviewRequest {

    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "RECOMMEND|DO_NOT_RECOMMEND|REQUEST_INFORMATION", 
             message = "Decision must be RECOMMEND, DO_NOT_RECOMMEND, or REQUEST_INFORMATION")
    private String decision;

    @NotBlank(message = "Comments are required")
    @Size(min = 10, max = 2000, message = "Comments must be between 10 and 2000 characters")
    private String comments;

    public CreateCreditReviewRequest() {}

    public CreateCreditReviewRequest(String decision, String comments) {
        this.decision = decision;
        this.comments = comments;
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
}

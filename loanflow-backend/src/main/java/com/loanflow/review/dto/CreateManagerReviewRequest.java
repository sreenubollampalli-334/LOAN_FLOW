package com.loanflow.review.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public class CreateManagerReviewRequest {

    @NotBlank(message = "Decision is required")
    @Pattern(regexp = "APPROVE|REJECT|RETURN_FOR_REVIEW",
             message = "Decision must be APPROVE, REJECT, or RETURN_FOR_REVIEW")
    private String decision;

    @NotBlank(message = "Comments are required")
    @Size(min = 10, max = 2000, message = "Comments must be between 10 and 2000 characters")
    private String comments;

    public CreateManagerReviewRequest() {}

    public CreateManagerReviewRequest(String decision, String comments) {
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

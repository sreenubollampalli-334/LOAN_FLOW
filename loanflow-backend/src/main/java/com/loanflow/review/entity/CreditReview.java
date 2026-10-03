package com.loanflow.review.entity;

import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.user.entity.User;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "credit_reviews", indexes = {
    @Index(name = "idx_cr_app_reviewed", columnList = "application_id, reviewed_at DESC, id DESC")
})
public class CreditReview {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "credit_officer_id", nullable = false)
    private User creditOfficer;

    @Column(nullable = false, length = 50)
    private String decision;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String comments;

    @Version
    @Column(nullable = false)
    private Integer version = 0;

    @Column(name = "reviewed_at", nullable = false)
    private LocalDateTime reviewedAt;

    public CreditReview() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LoanApplication getApplication() { return application; }
    public void setApplication(LoanApplication application) { this.application = application; }
    public User getCreditOfficer() { return creditOfficer; }
    public void setCreditOfficer(User creditOfficer) { this.creditOfficer = creditOfficer; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getComments() { return comments; }
    public void setComments(String comments) { this.comments = comments; }
    public Integer getVersion() { return version; }
    public void setVersion(Integer version) { this.version = version; }
    public LocalDateTime getReviewedAt() { return reviewedAt; }
    public void setReviewedAt(LocalDateTime reviewedAt) { this.reviewedAt = reviewedAt; }
}

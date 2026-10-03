package com.loanflow.eligibility.entity;

import com.loanflow.loan.entity.LoanApplication;
import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "eligibility_assessments", indexes = {
    @Index(name = "idx_ea_app_assessed", columnList = "application_id, assessed_at DESC")
})
public class EligibilityAssessment {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "application_id", nullable = false)
    private LoanApplication application;

    @Column(name = "ruleset_version", nullable = false, length = 50)
    private String rulesetVersion;

    @Column(nullable = false, length = 20)
    private String decision;

    @Column(name = "input_snapshot", nullable = false, columnDefinition = "JSON")
    private String inputSnapshot;

    @Column(name = "assessed_at", nullable = false)
    private LocalDateTime assessedAt;

    public EligibilityAssessment() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public LoanApplication getApplication() { return application; }
    public void setApplication(LoanApplication application) { this.application = application; }
    public String getRulesetVersion() { return rulesetVersion; }
    public void setRulesetVersion(String rulesetVersion) { this.rulesetVersion = rulesetVersion; }
    public String getDecision() { return decision; }
    public void setDecision(String decision) { this.decision = decision; }
    public String getInputSnapshot() { return inputSnapshot; }
    public void setInputSnapshot(String inputSnapshot) { this.inputSnapshot = inputSnapshot; }
    public LocalDateTime getAssessedAt() { return assessedAt; }
    public void setAssessedAt(LocalDateTime assessedAt) { this.assessedAt = assessedAt; }
}

package com.loanflow.eligibility.entity;

import jakarta.persistence.*;

@Entity
@Table(name = "eligibility_reason_codes")
public class EligibilityReasonCode {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "assessment_id", nullable = false)
    private EligibilityAssessment assessment;

    @Column(nullable = false, length = 50)
    private String code;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    public EligibilityReasonCode() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public EligibilityAssessment getAssessment() { return assessment; }
    public void setAssessment(EligibilityAssessment assessment) { this.assessment = assessment; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }
}

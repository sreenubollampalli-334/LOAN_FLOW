package com.loanflow.eligibility.repository;

import com.loanflow.eligibility.entity.EligibilityAssessment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EligibilityAssessmentRepository extends JpaRepository<EligibilityAssessment, Long> {
    List<EligibilityAssessment> findByApplicationIdOrderByAssessedAtDesc(Long applicationId);
}

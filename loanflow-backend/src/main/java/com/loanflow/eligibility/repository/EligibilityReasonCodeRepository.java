package com.loanflow.eligibility.repository;

import com.loanflow.eligibility.entity.EligibilityReasonCode;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface EligibilityReasonCodeRepository extends JpaRepository<EligibilityReasonCode, Long> {
    List<EligibilityReasonCode> findByAssessmentId(Long assessmentId);
}

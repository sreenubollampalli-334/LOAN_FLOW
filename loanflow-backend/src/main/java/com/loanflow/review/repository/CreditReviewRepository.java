package com.loanflow.review.repository;

import com.loanflow.review.entity.CreditReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface CreditReviewRepository extends JpaRepository<CreditReview, Long> {
    List<CreditReview> findByApplicationIdOrderByReviewedAtDesc(Long applicationId);
    List<CreditReview> findByCreditOfficerId(Long creditOfficerId);
}

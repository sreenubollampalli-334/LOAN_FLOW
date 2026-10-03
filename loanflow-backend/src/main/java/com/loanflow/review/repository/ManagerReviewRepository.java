package com.loanflow.review.repository;

import com.loanflow.review.entity.ManagerReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ManagerReviewRepository extends JpaRepository<ManagerReview, Long> {
    List<ManagerReview> findByApplicationIdOrderByReviewedAtDesc(Long applicationId);
    List<ManagerReview> findByManagerId(Long managerId);
}

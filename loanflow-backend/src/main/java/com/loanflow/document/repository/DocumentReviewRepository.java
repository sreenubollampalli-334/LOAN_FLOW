package com.loanflow.document.repository;

import com.loanflow.document.entity.DocumentReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface DocumentReviewRepository extends JpaRepository<DocumentReview, Long> {
    List<DocumentReview> findByDocumentIdOrderByReviewedAtDesc(Long documentId);
}

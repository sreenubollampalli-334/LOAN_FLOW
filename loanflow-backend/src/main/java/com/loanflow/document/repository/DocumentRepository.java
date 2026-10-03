package com.loanflow.document.repository;

import com.loanflow.document.entity.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {
    List<Document> findByApplicationId(Long applicationId);
    List<Document> findByCustomerId(Long customerId);
    Optional<Document> findByStorageKey(String storageKey);
}

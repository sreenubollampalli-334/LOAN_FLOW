package com.loanflow.product.repository;

import com.loanflow.product.entity.ProductRequiredDocument;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ProductRequiredDocumentRepository extends JpaRepository<ProductRequiredDocument, Long> {
    List<ProductRequiredDocument> findByProductId(Long productId);
    Optional<ProductRequiredDocument> findByProductIdAndDocumentType(Long productId, String documentType);
}

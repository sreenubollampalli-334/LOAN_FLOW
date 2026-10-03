package com.loanflow.product.repository;

import com.loanflow.product.entity.LoanProduct;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface LoanProductRepository extends JpaRepository<LoanProduct, Long> {
    Optional<LoanProduct> findByName(String name);
    org.springframework.data.domain.Page<LoanProduct> findByIsActiveTrue(org.springframework.data.domain.Pageable pageable);
    Optional<LoanProduct> findByIdAndIsActiveTrue(Long id);
}

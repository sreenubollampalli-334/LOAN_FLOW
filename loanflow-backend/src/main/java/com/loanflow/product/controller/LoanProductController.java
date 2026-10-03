package com.loanflow.product.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.product.dto.LoanProductDetailResponse;
import com.loanflow.product.dto.LoanProductSummaryResponse;
import com.loanflow.product.service.LoanProductService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan-products")
public class LoanProductController {

    private final LoanProductService loanProductService;

    public LoanProductController(LoanProductService loanProductService) {
        this.loanProductService = loanProductService;
    }

    @GetMapping
    public ResponseEntity<PaginatedResponse<LoanProductSummaryResponse>> getActiveProducts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(defaultValue = "id") String sortBy,
            @RequestParam(defaultValue = "ASC") String direction) {
        
        return ResponseEntity.ok(loanProductService.getActiveProducts(page, size, sortBy, direction));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoanProductDetailResponse> getActiveProductById(@PathVariable Long id) {
        return ResponseEntity.ok(loanProductService.getActiveProductById(id));
    }
}

package com.loanflow.loan.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.loan.dto.CreateLoanApplicationRequest;
import com.loanflow.loan.dto.LoanApplicationResponse;
import com.loanflow.loan.dto.LoanApplicationSummaryResponse;
import com.loanflow.loan.service.LoanApplicationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan-applications")
public class LoanApplicationController {

    private final LoanApplicationService loanApplicationService;

    public LoanApplicationController(LoanApplicationService loanApplicationService) {
        this.loanApplicationService = loanApplicationService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<LoanApplicationResponse> createDraft(@Valid @RequestBody CreateLoanApplicationRequest request) {
        LoanApplicationResponse response = loanApplicationService.createDraft(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaginatedResponse<LoanApplicationSummaryResponse>> getMyApplications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(loanApplicationService.getMyApplications(page, size));
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<LoanApplicationResponse> getMyApplicationById(@PathVariable Long id) {
        return ResponseEntity.ok(loanApplicationService.getMyApplicationById(id));
    }

    @PutMapping("/{id}")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<LoanApplicationResponse> updateDraft(
            @PathVariable Long id,
            @Valid @RequestBody com.loanflow.loan.dto.UpdateLoanApplicationRequest request) {
        return ResponseEntity.ok(loanApplicationService.updateDraft(id, request));
    }

    @PostMapping("/{id}/submit")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<LoanApplicationResponse> submitApplication(@PathVariable Long id) {
        return ResponseEntity.ok(loanApplicationService.submitApplication(id));
    }
}

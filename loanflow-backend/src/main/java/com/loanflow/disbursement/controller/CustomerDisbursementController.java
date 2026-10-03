package com.loanflow.disbursement.controller;

import com.loanflow.disbursement.dto.DisbursementResponse;
import com.loanflow.disbursement.service.DisbursementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loan-applications")
@Tag(name = "Customer Loan Disbursement", description = "Customer owner-scoped endpoint to view loan disbursement status.")
public class CustomerDisbursementController {

    private final DisbursementService disbursementService;

    public CustomerDisbursementController(DisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    @GetMapping("/{applicationId}/disbursement")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get customer loan disbursement status", description = "Retrieves safe simulated disbursement details for an owned loan application.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disbursement details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CUSTOMER role"),
            @ApiResponse(responseCode = "404", description = "Loan application or disbursement record not found")
    })
    public ResponseEntity<DisbursementResponse> getCustomerLoanDisbursement(@PathVariable Long applicationId) {
        DisbursementResponse response = disbursementService.getCustomerLoanDisbursement(applicationId);
        return ResponseEntity.ok(response);
    }
}

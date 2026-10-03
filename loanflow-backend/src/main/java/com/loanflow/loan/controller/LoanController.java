package com.loanflow.loan.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.loan.dto.LoanResponse;
import com.loanflow.loan.dto.LoanSummaryResponse;
import com.loanflow.loan.service.LoanService;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.RepaymentScheduleResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loans")
@Tag(name = "Loan Management", description = "Endpoints for loan retrieval, repayment schedules, and EMI calculation.")
public class LoanController {

    private final LoanService loanService;

    public LoanController(LoanService loanService) {
        this.loanService = loanService;
    }

    @GetMapping("/my-loans")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get customer loans", description = "Retrieves a paginated list of loans belonging to the authenticated customer.")
    public ResponseEntity<PaginatedResponse<LoanSummaryResponse>> getMyLoans(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        return ResponseEntity.ok(loanService.getMyLoans(page, size));
    }

    @GetMapping("/{loanId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Get loan details", description = "Retrieves loan details by ID. Customers can access only their own loans; Admins/Managers can access any loan.")
    public ResponseEntity<LoanResponse> getLoanById(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getLoanById(loanId));
    }

    @GetMapping("/{loanId}/schedule")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Get loan repayment schedule", description = "Retrieves the repayment schedule for a loan ordered by installment number. Customers can access only their own loan schedule.")
    public ResponseEntity<List<RepaymentScheduleResponse>> getLoanSchedule(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getLoanSchedule(loanId));
    }

    @PostMapping("/calculate-emi")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Preview EMI calculation", description = "Non-persistent calculation of EMI, total interest, total repayment, and installment breakdown.")
    public ResponseEntity<EmiCalculationResponse> calculateEmi(@Valid @RequestBody EmiCalculationRequest request) {
        return ResponseEntity.ok(loanService.calculateEmi(request));
    }

    @GetMapping("/{loanId}/repayment-summary")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'ADMIN', 'MANAGER')")
    @Operation(summary = "Get loan repayment summary", description = "Retrieves the repayment summary for a specific loan. Customers can access only their own loan summary.")
    public ResponseEntity<com.loanflow.repayment.dto.RepaymentSummaryResponse> getLoanRepaymentSummary(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanService.getLoanRepaymentSummary(loanId));
    }

    @GetMapping("/repayment-summary")
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Get customer repayment overview", description = "Retrieves the repayment overview for the authenticated customer across all active loans.")
    public ResponseEntity<com.loanflow.repayment.dto.CustomerRepaymentOverviewResponse> getCustomerRepaymentOverview() {
        return ResponseEntity.ok(loanService.getCustomerRepaymentOverview());
    }
}

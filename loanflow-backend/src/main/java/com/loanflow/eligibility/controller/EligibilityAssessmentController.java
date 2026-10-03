package com.loanflow.eligibility.controller;

import com.loanflow.eligibility.dto.EligibilityAssessmentResponse;
import com.loanflow.eligibility.service.EligibilityAssessmentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/loan-applications/{applicationId}/eligibility-assessments")
@Tag(name = "Eligibility Assessment", description = "Endpoints for deterministic demo loan application eligibility assessment")
public class EligibilityAssessmentController {

    private final EligibilityAssessmentService assessmentService;

    public EligibilityAssessmentController(EligibilityAssessmentService assessmentService) {
        this.assessmentService = assessmentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Perform demo eligibility assessment", description = "Evaluates a submitted loan application against product rules and mandatory document requirements. Educational demo only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Eligibility assessment performed and persisted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CUSTOMER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found or not owned by customer"),
            @ApiResponse(responseCode = "409", description = "Application is not in a SUBMITTED state")
    })
    public ResponseEntity<EligibilityAssessmentResponse> assessEligibility(@PathVariable Long applicationId) {
        EligibilityAssessmentResponse response = assessmentService.evaluateEligibility(applicationId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    @Operation(summary = "Retrieve eligibility assessment history", description = "Returns the historical list of eligibility assessments for the customer's loan application.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Assessment history retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CUSTOMER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found or not owned by customer")
    })
    public ResponseEntity<List<EligibilityAssessmentResponse>> getAssessmentHistory(@PathVariable Long applicationId) {
        List<EligibilityAssessmentResponse> history = assessmentService.getAssessmentHistory(applicationId);
        return ResponseEntity.ok(history);
    }
}

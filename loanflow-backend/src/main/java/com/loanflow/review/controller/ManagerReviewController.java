package com.loanflow.review.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.review.dto.CreateManagerReviewRequest;
import com.loanflow.review.dto.ManagerReviewDetailsResponse;
import com.loanflow.review.dto.ManagerReviewQueueItemResponse;
import com.loanflow.review.dto.ManagerReviewResponse;
import com.loanflow.review.service.ManagerReviewService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/manager-reviews")
@Tag(name = "Manager Review", description = "Endpoints for manager review & approval workflow. Educational demonstration only.")
public class ManagerReviewController {

    private final ManagerReviewService managerReviewService;

    public ManagerReviewController(ManagerReviewService managerReviewService) {
        this.managerReviewService = managerReviewService;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get manager application queue", description = "Retrieves a paginated list of SUBMITTED loan applications with completed eligibility assessments and credit officer reviews awaiting manager action.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Manager review queue retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires MANAGER role")
    })
    public ResponseEntity<PaginatedResponse<ManagerReviewQueueItemResponse>> getQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<ManagerReviewQueueItemResponse> response = managerReviewService.getQueue(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Get manager review details for application", description = "Retrieves comprehensive review context including application summary, credit officer recommendation, eligibility assessment, and document statuses for a manager.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Manager review details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires MANAGER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found")
    })
    public ResponseEntity<ManagerReviewDetailsResponse> getReviewDetails(@PathVariable Long applicationId) {
        ManagerReviewDetailsResponse response = managerReviewService.getReviewDetails(applicationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('MANAGER')")
    @Operation(summary = "Record manager review decision", description = "Submits a manager decision (APPROVE, REJECT, RETURN_FOR_REVIEW) for a loan application. If APPROVED, creates a demo Loan record. Educational demonstration only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Manager decision recorded, status updated, and demo Loan created successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid review request fields or comment length"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires MANAGER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found"),
            @ApiResponse(responseCode = "409", description = "Application not in SUBMITTED state, missing credit review, duplicate manager review, or decision conflicts with eligibility outcome")
    })
    public ResponseEntity<ManagerReviewResponse> createReview(
            @PathVariable Long applicationId,
            @Valid @RequestBody CreateManagerReviewRequest request,
            HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr();
        ManagerReviewResponse response = managerReviewService.createReview(applicationId, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

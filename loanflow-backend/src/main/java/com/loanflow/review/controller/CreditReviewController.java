package com.loanflow.review.controller;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.review.dto.CreateCreditReviewRequest;
import com.loanflow.review.dto.CreditReviewDetailsResponse;
import com.loanflow.review.dto.CreditReviewQueueItemResponse;
import com.loanflow.review.dto.CreditReviewResponse;
import com.loanflow.review.service.CreditReviewService;
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
@RequestMapping("/api/v1/credit-reviews")
@Tag(name = "Credit Review", description = "Endpoints for credit officer loan application review workflow. Educational demonstration only.")
public class CreditReviewController {

    private final CreditReviewService creditReviewService;

    public CreditReviewController(CreditReviewService creditReviewService) {
        this.creditReviewService = creditReviewService;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('CREDIT_OFFICER')")
    @Operation(summary = "Get credit officer application queue", description = "Retrieves a paginated list of SUBMITTED loan applications with completed eligibility assessments awaiting credit review.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Review queue retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CREDIT_OFFICER role")
    })
    public ResponseEntity<PaginatedResponse<CreditReviewQueueItemResponse>> getQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<CreditReviewQueueItemResponse> response = creditReviewService.getQueue(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('CREDIT_OFFICER')")
    @Operation(summary = "Get credit review details for application", description = "Retrieves comprehensive review context including application details, eligibility assessment, and document statuses for a credit officer.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Application review details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CREDIT_OFFICER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found")
    })
    public ResponseEntity<CreditReviewDetailsResponse> getReviewDetails(@PathVariable Long applicationId) {
        CreditReviewDetailsResponse response = creditReviewService.getReviewDetails(applicationId);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/applications/{applicationId}")
    @PreAuthorize("hasRole('CREDIT_OFFICER')")
    @Operation(summary = "Record credit officer review decision", description = "Submits a credit officer recommendation (RECOMMEND, DO_NOT_RECOMMEND, REQUEST_INFORMATION) for a loan application. Educational demonstration only.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Credit review recorded and audit log persisted successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid review request fields or comment length"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CREDIT_OFFICER role"),
            @ApiResponse(responseCode = "404", description = "Loan application not found"),
            @ApiResponse(responseCode = "409", description = "Application not in SUBMITTED state, missing eligibility assessment, duplicate review, or decision conflicts with eligibility outcome")
    })
    public ResponseEntity<CreditReviewResponse> createReview(
            @PathVariable Long applicationId,
            @Valid @RequestBody CreateCreditReviewRequest request,
            HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr();
        CreditReviewResponse response = creditReviewService.createReview(applicationId, request, clientIp);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/applications/{applicationId}/documents/{documentId}/review")
    @PreAuthorize("hasRole('CREDIT_OFFICER')")
    @Operation(summary = "Record document review decision", description = "Submits a document verification (VERIFIED, REJECTED).")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Document review recorded successfully"),
            @ApiResponse(responseCode = "400", description = "Invalid request or missing rejection reason"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires CREDIT_OFFICER role"),
            @ApiResponse(responseCode = "404", description = "Loan application or document not found"),
            @ApiResponse(responseCode = "409", description = "Application not in SUBMITTED state, document already reviewed, or final credit review already exists")
    })
    public ResponseEntity<Void> reviewDocument(
            @PathVariable Long applicationId,
            @PathVariable Long documentId,
            @Valid @RequestBody com.loanflow.review.dto.DocumentReviewRequest request) {
        creditReviewService.reviewDocument(applicationId, documentId, request);
        return ResponseEntity.ok().build();
    }
}

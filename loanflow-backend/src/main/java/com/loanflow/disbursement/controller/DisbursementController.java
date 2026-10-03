package com.loanflow.disbursement.controller;

import com.loanflow.disbursement.dto.*;
import com.loanflow.disbursement.service.DisbursementService;
import com.loanflow.dto.PaginatedResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/disbursements")
@Tag(name = "Loan Disbursement", description = "Endpoints for simulated demo loan disbursement workflow. Educational demonstration only.")
public class DisbursementController {

    private final DisbursementService disbursementService;

    public DisbursementController(DisbursementService disbursementService) {
        this.disbursementService = disbursementService;
    }

    @GetMapping("/queue")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Get disbursement queue", description = "Retrieves a paginated list of approved loans awaiting simulated disbursement.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disbursement queue retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role")
    })
    public ResponseEntity<PaginatedResponse<DisbursementQueueItemResponse>> getQueue(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "10") int size) {
        PaginatedResponse<DisbursementQueueItemResponse> response = disbursementService.getQueue(page, size);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{disbursementId}")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get disbursement details", description = "Retrieves safe metadata and status for a simulated loan disbursement.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disbursement details retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN or MANAGER role"),
            @ApiResponse(responseCode = "404", description = "Disbursement not found")
    })
    public ResponseEntity<DisbursementResponse> getDisbursementById(@PathVariable Long disbursementId) {
        DisbursementResponse response = disbursementService.getDisbursementById(disbursementId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{disbursementId}/attempts")
    @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
    @Operation(summary = "Get disbursement attempt history", description = "Retrieves historical attempt records for a simulated loan disbursement.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Disbursement attempts retrieved successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN or MANAGER role"),
            @ApiResponse(responseCode = "404", description = "Disbursement not found")
    })
    public ResponseEntity<List<DisbursementAttemptResponse>> getDisbursementAttempts(@PathVariable Long disbursementId) {
        List<DisbursementAttemptResponse> attempts = disbursementService.getDisbursementAttempts(disbursementId);
        return ResponseEntity.ok(attempts);
    }

    @PostMapping("/loans/{loanId}")
    @PreAuthorize("hasRole('ADMIN')")
    @Operation(summary = "Initiate simulated loan disbursement", description = "Executes a simulated loan disbursement for an approved loan. Educational demonstration only; no real money is transferred.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "201", description = "Simulated disbursement processed and persisted successfully"),
            @ApiResponse(responseCode = "401", description = "Unauthenticated request"),
            @ApiResponse(responseCode = "403", description = "Forbidden - Requires ADMIN role"),
            @ApiResponse(responseCode = "404", description = "Loan not found"),
            @ApiResponse(responseCode = "409", description = "Loan not in APPROVED status, already disbursed, or retry limit exceeded")
    })
    public ResponseEntity<DisbursementResponse> initiateDisbursement(
            @PathVariable Long loanId,
            @RequestBody(required = false) InitiateDisbursementRequest request,
            @RequestHeader(value = "X-Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Test-Simulate-Failure", required = false) String testSimulateFailureHeader,
            @RequestHeader(value = "X-Test-Failure-Reason", required = false) String testFailureReasonHeader,
            HttpServletRequest httpServletRequest) {
        String clientIp = httpServletRequest.getRemoteAddr();
        DisbursementResponse response = disbursementService.initiateDisbursement(
                loanId, request, idempotencyKey, clientIp, testSimulateFailureHeader, testFailureReasonHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}

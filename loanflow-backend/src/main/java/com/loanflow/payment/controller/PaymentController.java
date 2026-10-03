package com.loanflow.payment.controller;

import com.loanflow.payment.dto.PaymentRequest;
import com.loanflow.payment.dto.PaymentResponse;
import com.loanflow.payment.service.PaymentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.loanflow.payment.dto.PaymentDetailsResponse;
import com.loanflow.payment.dto.RefundHistoryResponse;
import java.util.List;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        PaymentResponse response = paymentService.createPayment(request, idempotencyKey);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @PostMapping("/{paymentId}/refunds")
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<PaymentResponse> createRefund(
            @PathVariable Long paymentId,
            @Valid @RequestBody com.loanflow.payment.dto.RefundRequest request,
            @RequestHeader("Idempotency-Key") String idempotencyKey) {
        
        PaymentResponse response = paymentService.createRefund(paymentId, request, idempotencyKey);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping
    @PreAuthorize("hasRole('CUSTOMER')")
    public ResponseEntity<Page<PaymentResponse>> getPaymentHistory(
            @RequestParam(required = false) Long loanId,
            @RequestParam(required = false) String status,
            Pageable pageable) {
        Page<PaymentResponse> response = paymentService.getPaymentHistory(loanId, status, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'MANAGER')")
    public ResponseEntity<PaymentDetailsResponse> getPaymentDetails(@PathVariable Long paymentId) {
        PaymentDetailsResponse response = paymentService.getPaymentDetails(paymentId);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{paymentId}/refunds")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'MANAGER')")
    public ResponseEntity<List<RefundHistoryResponse>> getRefundHistory(@PathVariable Long paymentId) {
        List<RefundHistoryResponse> response = paymentService.getRefundHistory(paymentId);
        return ResponseEntity.ok(response);
    }
}

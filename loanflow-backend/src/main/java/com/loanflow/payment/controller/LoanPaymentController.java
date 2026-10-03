package com.loanflow.payment.controller;

import com.loanflow.payment.dto.PaymentResponse;
import com.loanflow.payment.service.PaymentService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import com.loanflow.payment.dto.PaymentReconciliationResponse;
import com.loanflow.payment.service.PaymentReconciliationService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/loans")
public class LoanPaymentController {

    private final PaymentService paymentService;
    private final PaymentReconciliationService paymentReconciliationService;

    public LoanPaymentController(PaymentService paymentService, PaymentReconciliationService paymentReconciliationService) {
        this.paymentService = paymentService;
        this.paymentReconciliationService = paymentReconciliationService;
    }

    @GetMapping("/{loanId}/payments")
    @PreAuthorize("hasAnyRole('CUSTOMER', 'STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<Page<PaymentResponse>> getLoanPaymentHistory(
            @PathVariable Long loanId,
            Pageable pageable) {
        Page<PaymentResponse> response = paymentService.getLoanPaymentHistory(loanId, pageable);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{loanId}/payment-reconciliation")
    @PreAuthorize("hasAnyRole('STAFF', 'MANAGER', 'ADMIN')")
    public ResponseEntity<PaymentReconciliationResponse> reconcileLoanPayments(@PathVariable Long loanId) {
        PaymentReconciliationResponse response = paymentReconciliationService.reconcileLoan(loanId);
        return ResponseEntity.ok(response);
    }
}

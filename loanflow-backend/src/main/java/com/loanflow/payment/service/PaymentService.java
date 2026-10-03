package com.loanflow.payment.service;

import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.payment.dto.PaymentRequest;
import com.loanflow.payment.dto.PaymentResponse;
import com.loanflow.payment.entity.Payment;
import com.loanflow.payment.enums.PaymentStatus;
import com.loanflow.payment.repository.PaymentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.List;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.loanflow.audit.service.AuditService;
import com.loanflow.payment.dto.PaymentDetailsResponse;
import com.loanflow.payment.dto.PaymentAllocationDto;
import com.loanflow.payment.dto.RefundHistoryResponse;
import com.loanflow.payment.dto.RefundAllocationDto;

@Service
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final LoanRepository loanRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final com.loanflow.repayment.repository.RepaymentScheduleRepository repaymentScheduleRepository;
    private final com.loanflow.payment.repository.PaymentAllocationRepository paymentAllocationRepository;
    private final com.loanflow.payment.repository.RefundAllocationRepository refundAllocationRepository;
    private final AuditService auditService;
    private final com.loanflow.outbox.repository.OutboxEventRepository outboxEventRepository;

    public PaymentService(PaymentRepository paymentRepository, LoanRepository loanRepository, CustomerProfileRepository customerProfileRepository,
                          com.loanflow.repayment.repository.RepaymentScheduleRepository repaymentScheduleRepository,
                          com.loanflow.payment.repository.PaymentAllocationRepository paymentAllocationRepository,
                          com.loanflow.payment.repository.RefundAllocationRepository refundAllocationRepository,
                          AuditService auditService,
                          com.loanflow.outbox.repository.OutboxEventRepository outboxEventRepository) {
        this.paymentRepository = paymentRepository;
        this.loanRepository = loanRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.refundAllocationRepository = refundAllocationRepository;
        this.auditService = auditService;
        this.outboxEventRepository = outboxEventRepository;
    }

    @Transactional
    public PaymentResponse createPayment(PaymentRequest request, String idempotencyKey) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        
        Loan loan = loanRepository.findByIdAndCustomerId(request.getLoanId(), customer.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found or does not belong to the customer"));

        if (!"DISBURSED".equals(loan.getStatus())) {
            throw new ResourceConflictException("Cannot make a payment on a loan that is not in DISBURSED status");
        }

        String requestHash = generateRequestHash(request);

        // Check for existing payment with the same idempotency key
        Optional<Payment> existingPaymentOpt = paymentRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
        if (existingPaymentOpt.isPresent()) {
            return handleIdempotencyConflict(existingPaymentOpt.get(), requestHash);
        }

        Payment payment = new Payment();
        payment.setLoan(loan);
        payment.setCustomer(customer);
        payment.setAmount(request.getAmount());
        payment.setUnappliedAmount(request.getAmount());
        payment.setPaymentDate(LocalDateTime.now());
        payment.setIdempotencyKey(idempotencyKey);
        payment.setRequestHash(requestHash);
        payment.setReferenceNumber(UUID.randomUUID().toString());
        payment.setStatus(PaymentStatus.SUCCESS.name());

        try {
            Payment savedPayment = paymentRepository.saveAndFlush(payment);
            allocatePayment(savedPayment);
            
            checkAndCloseLoan(loan);

            String jsonPayload = String.format("{\"amount\":%s,\"loanId\":%d}", savedPayment.getAmount(), loan.getId());
            auditService.recordAudit("PAYMENT_CREATED", "Payment", savedPayment.getId(), null, jsonPayload, "system");
            
            com.loanflow.outbox.entity.OutboxEvent outboxEvent = new com.loanflow.outbox.entity.OutboxEvent();
            outboxEvent.setAggregateType("Payment");
            outboxEvent.setAggregateId(savedPayment.getId());
            outboxEvent.setType("PAYMENT_RECEIVED");
            outboxEvent.setPayload(jsonPayload);
            outboxEvent.setStatus("PENDING");
            outboxEventRepository.save(outboxEvent);

            return mapToResponse(savedPayment);
        } catch (DataIntegrityViolationException e) {
            // Concurrent request might have created the payment just before we tried
            existingPaymentOpt = paymentRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
            if (existingPaymentOpt.isPresent()) {
                return handleIdempotencyConflict(existingPaymentOpt.get(), requestHash);
            }
            throw new ResourceConflictException("Failed to process payment due to data conflict");
        }
    }

    private void allocatePayment(Payment payment) {
        // 1. Lock payment first
        Payment lockedPayment = paymentRepository.findByIdWithLock(payment.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found for allocation"));

        // 2. Lock repayment schedules in ascending ID order
        java.util.List<com.loanflow.repayment.entity.RepaymentSchedule> schedules = 
                repaymentScheduleRepository.findPendingSchedulesForUpdateByLoanId(lockedPayment.getLoan().getId());

        // Sort schedules by installment number ascending
        schedules.sort(java.util.Comparator.comparing(com.loanflow.repayment.entity.RepaymentSchedule::getInstallmentNumber));

        java.math.BigDecimal remainingAmount = lockedPayment.getUnappliedAmount();

        for (com.loanflow.repayment.entity.RepaymentSchedule schedule : schedules) {
            if (remainingAmount.compareTo(java.math.BigDecimal.ZERO) <= 0) {
                break;
            }

            java.math.BigDecimal outstanding = schedule.getTotalAmount()
                    .subtract(schedule.getAmountPaid())
                    .add(schedule.getAmountRefunded());

            if (outstanding.compareTo(java.math.BigDecimal.ZERO) > 0) {
                java.math.BigDecimal amountToAllocate = remainingAmount.min(outstanding);
                
                com.loanflow.payment.entity.PaymentAllocation allocation = new com.loanflow.payment.entity.PaymentAllocation();
                allocation.setPayment(lockedPayment);
                allocation.setRepaymentSchedule(schedule);
                allocation.setLoan(lockedPayment.getLoan());
                allocation.setAllocatedAmount(amountToAllocate);
                paymentAllocationRepository.save(allocation);

                schedule.setAmountPaid(schedule.getAmountPaid().add(amountToAllocate));
                java.math.BigDecimal newOutstanding = schedule.getTotalAmount()
                        .subtract(schedule.getAmountPaid())
                        .add(schedule.getAmountRefunded());
                if (newOutstanding.compareTo(java.math.BigDecimal.ZERO) == 0) {
                    schedule.setStatus("PAID");
                } else {
                    schedule.setStatus("PARTIALLY_PAID");
                }
                repaymentScheduleRepository.save(schedule);

                remainingAmount = remainingAmount.subtract(amountToAllocate);
            }
        }

        lockedPayment.setUnappliedAmount(remainingAmount);
        paymentRepository.save(lockedPayment);
    }

    private void checkAndCloseLoan(Loan loan) {
        java.util.List<com.loanflow.repayment.entity.RepaymentSchedule> schedules = 
                repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        
        if (schedules.isEmpty()) return;

        boolean allPaid = schedules.stream().allMatch(s -> "PAID".equals(s.getStatus()));
        if (allPaid && "DISBURSED".equals(loan.getStatus())) {
            loan.setStatus("CLOSED");
            loanRepository.save(loan);
        }
    }

    private PaymentResponse handleIdempotencyConflict(Payment existingPayment, String requestHash) {
        if (existingPayment.getRequestHash().equals(requestHash)) {
            return mapToResponse(existingPayment);
        } else {
            throw new ResourceConflictException("Idempotency conflict: a different request with the same idempotency key already exists");
        }
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        Long userId = userDetails.getUser().getId();
        
        return customerProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private String generateRequestHash(PaymentRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = request.getLoanId() + ":" + request.getAmount().toPlainString();
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate request hash", e);
        }
    }

    @Transactional
    public PaymentResponse createRefund(Long paymentId, com.loanflow.payment.dto.RefundRequest request, String idempotencyKey) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        
        Payment originalPayment = paymentRepository.findByIdWithLock(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        if (!originalPayment.getCustomer().getId().equals(customer.getId())) {
            throw new ResourceNotFoundException("Payment not found");
        }
        
        if ("REFUND".equals(originalPayment.getType())) {
            throw new ResourceConflictException("Cannot refund a refund transaction");
        }
        
        if (!PaymentStatus.SUCCESS.name().equals(originalPayment.getStatus())) {
            throw new ResourceConflictException("Cannot refund a payment that is not successful");
        }

        String requestHash = generateRefundRequestHash(paymentId, request);

        Optional<Payment> existingRefundOpt = paymentRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
        if (existingRefundOpt.isPresent()) {
            return handleIdempotencyConflict(existingRefundOpt.get(), requestHash);
        }

        java.math.BigDecimal refundableUnapplied = originalPayment.getUnappliedAmount().subtract(originalPayment.getRefundedUnappliedAmount());
        
        java.math.BigDecimal refundableAllocated = java.math.BigDecimal.ZERO;
        java.util.List<com.loanflow.payment.entity.PaymentAllocation> originalAllocations = paymentAllocationRepository.findByPaymentId(originalPayment.getId());
        
        java.util.Map<Long, java.math.BigDecimal> refundedPerAllocation = new java.util.HashMap<>();
        for (com.loanflow.payment.entity.PaymentAllocation pa : originalAllocations) {
            java.util.List<com.loanflow.payment.entity.RefundAllocation> ras = refundAllocationRepository.findByOriginalAllocationId(pa.getId());
            java.math.BigDecimal refunded = ras.stream()
                .map(com.loanflow.payment.entity.RefundAllocation::getRefundedAmount)
                .reduce(java.math.BigDecimal.ZERO, java.math.BigDecimal::add);
            refundedPerAllocation.put(pa.getId(), refunded);
            refundableAllocated = refundableAllocated.add(pa.getAllocatedAmount().subtract(refunded));
        }
        
        java.math.BigDecimal totalRefundable = refundableUnapplied.add(refundableAllocated);
        if (request.getAmount().compareTo(totalRefundable) > 0) {
            throw new ResourceConflictException("Refund amount exceeds refundable amount");
        }

        Payment refund = new Payment();
        refund.setLoan(originalPayment.getLoan());
        refund.setCustomer(customer);
        refund.setType("REFUND");
        refund.setOriginalPayment(originalPayment);
        refund.setAmount(request.getAmount());
        refund.setUnappliedAmount(java.math.BigDecimal.ZERO);
        refund.setPaymentDate(LocalDateTime.now());
        refund.setIdempotencyKey(idempotencyKey);
        refund.setRequestHash(requestHash);
        refund.setReferenceNumber(UUID.randomUUID().toString());
        refund.setStatus(PaymentStatus.SUCCESS.name());

        try {
            Payment savedRefund = paymentRepository.saveAndFlush(refund);
            allocateRefund(savedRefund, originalPayment, request.getAmount(), refundableUnapplied, originalAllocations, refundedPerAllocation);
            String jsonPayload = String.format("{\"amount\":%s,\"originalPaymentId\":%d}", savedRefund.getAmount(), originalPayment.getId());
            auditService.recordAudit("REFUND_CREATED", "Payment", savedRefund.getId(), null, jsonPayload, "system");
            return mapToResponse(savedRefund);
        } catch (DataIntegrityViolationException e) {
            existingRefundOpt = paymentRepository.findByCustomerIdAndIdempotencyKey(customer.getId(), idempotencyKey);
            if (existingRefundOpt.isPresent()) {
                return handleIdempotencyConflict(existingRefundOpt.get(), requestHash);
            }
            throw new ResourceConflictException("Failed to process refund due to data conflict");
        }
    }

    private String generateRefundRequestHash(Long paymentId, com.loanflow.payment.dto.RefundRequest request) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            String input = "REFUND:" + paymentId + ":" + request.getAmount().toPlainString();
            byte[] encodedhash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("Failed to generate request hash", e);
        }
    }

    private void allocateRefund(Payment refundPayment, Payment originalPayment, java.math.BigDecimal refundAmount, 
        java.math.BigDecimal refundableUnapplied, java.util.List<com.loanflow.payment.entity.PaymentAllocation> originalAllocations,
        java.util.Map<Long, java.math.BigDecimal> refundedPerAllocation) {
        
        java.math.BigDecimal remainingRefund = refundAmount;
        
        // 1. Refund from unapplied amount first
        if (refundableUnapplied.compareTo(java.math.BigDecimal.ZERO) > 0) {
            java.math.BigDecimal amountFromUnapplied = remainingRefund.min(refundableUnapplied);
            originalPayment.setRefundedUnappliedAmount(originalPayment.getRefundedUnappliedAmount().add(amountFromUnapplied));
            remainingRefund = remainingRefund.subtract(amountFromUnapplied);
        }
        
        // 2. Refund from allocations in deterministic order (descending installment number to keep oldest paid)
        if (remainingRefund.compareTo(java.math.BigDecimal.ZERO) > 0 && !originalAllocations.isEmpty()) {
            java.util.List<Long> scheduleIdsToLock = originalAllocations.stream()
                .map(pa -> pa.getRepaymentSchedule().getId())
                .distinct()
                .sorted()
                .collect(java.util.stream.Collectors.toList());
                
            java.util.List<com.loanflow.repayment.entity.RepaymentSchedule> lockedSchedules = 
                repaymentScheduleRepository.findByIdInWithLock(scheduleIdsToLock);
                
            java.util.Map<Long, com.loanflow.repayment.entity.RepaymentSchedule> scheduleMap = lockedSchedules.stream()
                .collect(java.util.stream.Collectors.toMap(com.loanflow.repayment.entity.RepaymentSchedule::getId, s -> s));

            originalAllocations.sort((a, b) -> Integer.compare(b.getRepaymentSchedule().getInstallmentNumber(), a.getRepaymentSchedule().getInstallmentNumber()));

            for (com.loanflow.payment.entity.PaymentAllocation pa : originalAllocations) {
                if (remainingRefund.compareTo(java.math.BigDecimal.ZERO) <= 0) break;
                
                java.math.BigDecimal alreadyRefunded = refundedPerAllocation.get(pa.getId());
                java.math.BigDecimal allocationRefundable = pa.getAllocatedAmount().subtract(alreadyRefunded);
                
                if (allocationRefundable.compareTo(java.math.BigDecimal.ZERO) > 0) {
                    java.math.BigDecimal amountToRefund = remainingRefund.min(allocationRefundable);
                    
                    com.loanflow.payment.entity.RefundAllocation refundAllocation = new com.loanflow.payment.entity.RefundAllocation();
                    refundAllocation.setRefundPayment(refundPayment);
                    refundAllocation.setOriginalAllocation(pa);
                    refundAllocation.setRepaymentSchedule(pa.getRepaymentSchedule());
                    refundAllocation.setLoan(originalPayment.getLoan());
                    refundAllocation.setRefundedAmount(amountToRefund);
                    refundAllocationRepository.save(refundAllocation);
                    
                    com.loanflow.repayment.entity.RepaymentSchedule schedule = scheduleMap.get(pa.getRepaymentSchedule().getId());
                    if (schedule.getAmountRefunded() == null) {
                        schedule.setAmountRefunded(java.math.BigDecimal.ZERO);
                    }
                    schedule.setAmountRefunded(schedule.getAmountRefunded().add(amountToRefund));
                    
                    java.math.BigDecimal outstanding = schedule.getTotalAmount()
                        .subtract(schedule.getAmountPaid())
                        .add(schedule.getAmountRefunded());
                    
                    if (outstanding.compareTo(java.math.BigDecimal.ZERO) == 0) {
                        schedule.setStatus("PAID");
                    } else if (schedule.getAmountPaid().subtract(schedule.getAmountRefunded()).compareTo(java.math.BigDecimal.ZERO) > 0) {
                        schedule.setStatus("PARTIALLY_PAID");
                    } else {
                        schedule.setStatus("PENDING");
                    }
                    
                    repaymentScheduleRepository.save(schedule);
                    remainingRefund = remainingRefund.subtract(amountToRefund);
                }
            }
        }
        
        paymentRepository.save(originalPayment);
    }

    private PaymentResponse mapToResponse(Payment payment) {
        PaymentResponse response = new PaymentResponse();
        response.setId(payment.getId());
        response.setLoanId(payment.getLoan().getId());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setPaymentDate(payment.getPaymentDate());
        response.setReferenceNumber(payment.getReferenceNumber());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        return response;
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getPaymentHistory(Long loanId, String status, Pageable pageable) {
        CustomerProfile customer = getAuthenticatedCustomerProfile();
        Page<Payment> payments = paymentRepository.findPaymentsByCustomer(customer.getId(), loanId, status, pageable);
        return payments.map(this::mapToResponse);
    }

    @Transactional(readOnly = true)
    public PaymentDetailsResponse getPaymentDetails(Long paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        // Enforce ownership or staff access
        if (userDetails.getAuthorities().stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_STAFF") || a.getAuthority().startsWith("ROLE_MANAGER") || a.getAuthority().startsWith("ROLE_ADMIN"))) {
            CustomerProfile customer = customerProfileRepository.findByUserId(userDetails.getUser().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
            if (!payment.getCustomer().getId().equals(customer.getId())) {
                throw new ResourceNotFoundException("Payment not found");
            }
        }

        PaymentDetailsResponse response = new PaymentDetailsResponse();
        response.setId(payment.getId());
        response.setLoanId(payment.getLoan().getId());
        response.setAmount(payment.getAmount());
        response.setStatus(payment.getStatus());
        response.setPaymentDate(payment.getPaymentDate());
        response.setReferenceNumber(payment.getReferenceNumber());
        response.setIdempotencyKey(payment.getIdempotencyKey());
        response.setUnappliedAmount(payment.getUnappliedAmount());
        response.setRefundedUnappliedAmount(payment.getRefundedUnappliedAmount());

        List<com.loanflow.payment.entity.PaymentAllocation> allocations = paymentAllocationRepository.findByPaymentId(paymentId);
        List<PaymentAllocationDto> allocationDtos = allocations.stream().map(a -> {
            PaymentAllocationDto dto = new PaymentAllocationDto();
            dto.setId(a.getId());
            dto.setRepaymentScheduleId(a.getRepaymentSchedule().getId());
            dto.setAllocatedAmount(a.getAllocatedAmount());
            dto.setCreatedAt(a.getCreatedAt());
            return dto;
        }).collect(Collectors.toList());
        response.setAllocations(allocationDtos);

        return response;
    }

    @Transactional(readOnly = true)
    public List<RefundHistoryResponse> getRefundHistory(Long paymentId) {
        Payment originalPayment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResourceNotFoundException("Payment not found"));

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (userDetails.getAuthorities().stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_STAFF") || a.getAuthority().startsWith("ROLE_MANAGER") || a.getAuthority().startsWith("ROLE_ADMIN"))) {
            CustomerProfile customer = customerProfileRepository.findByUserId(userDetails.getUser().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
            if (!originalPayment.getCustomer().getId().equals(customer.getId())) {
                throw new ResourceNotFoundException("Payment not found");
            }
        }

        List<Payment> refunds = paymentRepository.findByOriginalPaymentId(paymentId);

        return refunds.stream().map(refund -> {
            RefundHistoryResponse resp = new RefundHistoryResponse();
            resp.setId(refund.getId());
            resp.setAmount(refund.getAmount());
            resp.setStatus(refund.getStatus());
            resp.setPaymentDate(refund.getPaymentDate());
            resp.setReferenceNumber(refund.getReferenceNumber());

            List<com.loanflow.payment.entity.RefundAllocation> refundAllocs = refundAllocationRepository.findByRefundPaymentId(refund.getId());
            List<RefundAllocationDto> allocDtos = refundAllocs.stream().map(ra -> {
                RefundAllocationDto dto = new RefundAllocationDto();
                dto.setId(ra.getId());
                dto.setOriginalAllocationId(ra.getOriginalAllocation().getId());
                dto.setRepaymentScheduleId(ra.getRepaymentSchedule().getId());
                dto.setRefundedAmount(ra.getRefundedAmount());
                dto.setCreatedAt(ra.getCreatedAt());
                return dto;
            }).collect(Collectors.toList());
            resp.setRefundAllocations(allocDtos);

            return resp;
        }).collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public Page<PaymentResponse> getLoanPaymentHistory(Long loanId, Pageable pageable) {
        Loan loan = loanRepository.findById(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        UserDetailsImpl userDetails = (UserDetailsImpl) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        
        if (userDetails.getAuthorities().stream().noneMatch(a -> a.getAuthority().startsWith("ROLE_STAFF") || a.getAuthority().startsWith("ROLE_MANAGER") || a.getAuthority().startsWith("ROLE_ADMIN"))) {
            CustomerProfile customer = customerProfileRepository.findByUserId(userDetails.getUser().getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
            if (!loan.getCustomer().getId().equals(customer.getId())) {
                throw new ResourceNotFoundException("Loan not found");
            }
        }

        Page<Payment> payments = paymentRepository.findByLoanId(loanId, pageable);
        return payments.map(this::mapToResponse);
    }
}

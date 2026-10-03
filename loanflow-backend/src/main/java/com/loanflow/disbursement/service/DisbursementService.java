package com.loanflow.disbursement.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.disbursement.dto.*;
import com.loanflow.disbursement.entity.Disbursement;
import com.loanflow.disbursement.entity.DisbursementAttempt;
import com.loanflow.disbursement.entity.DisbursementIdempotencyKey;
import com.loanflow.disbursement.repository.DisbursementAttemptRepository;
import com.loanflow.disbursement.repository.DisbursementIdempotencyKeyRepository;
import com.loanflow.disbursement.repository.DisbursementRepository;
import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import com.loanflow.repayment.service.RepaymentScheduleService;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class DisbursementService {

    public static final String DEMO_DISBURSEMENT_DISCLAIMER = "Simulated demo loan disbursement. No real funds were transferred.";
    public static final int MAX_RETRY_ATTEMPTS = 3;

    private final DisbursementRepository disbursementRepository;
    private final DisbursementAttemptRepository attemptRepository;
    private final DisbursementIdempotencyKeyRepository idempotencyKeyRepository;
    private final LoanRepository loanRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;
    private final DisbursementProcessor disbursementProcessor;
    private final ObjectMapper objectMapper;
    private final RepaymentScheduleService repaymentScheduleService;

    public DisbursementService(DisbursementRepository disbursementRepository,
                               DisbursementAttemptRepository attemptRepository,
                               DisbursementIdempotencyKeyRepository idempotencyKeyRepository,
                               LoanRepository loanRepository,
                               LoanApplicationRepository loanApplicationRepository,
                               CustomerProfileRepository customerProfileRepository,
                               UserRepository userRepository,
                               AuditService auditService,
                               OutboxEventRepository outboxEventRepository,
                               DisbursementProcessor disbursementProcessor,
                               ObjectMapper objectMapper,
                               RepaymentScheduleService repaymentScheduleService) {
        this.disbursementRepository = disbursementRepository;
        this.attemptRepository = attemptRepository;
        this.idempotencyKeyRepository = idempotencyKeyRepository;
        this.loanRepository = loanRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.outboxEventRepository = outboxEventRepository;
        this.disbursementProcessor = disbursementProcessor;
        this.objectMapper = objectMapper;
        this.repaymentScheduleService = repaymentScheduleService;
    }

    private User getAuthenticatedAdmin() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            boolean isAdmin = user.getRoles().stream()
                    .anyMatch(r -> "ADMIN".equalsIgnoreCase(r.getName()) || "ROLE_ADMIN".equalsIgnoreCase(r.getName()));

            if (!isAdmin) {
                throw new AccessDeniedException("Access denied: Admin role required for disbursement operations");
            }
            return user;
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
            return customerProfileRepository.findByUserId(user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    private String calculateRequestHash(Long loanId, InitiateDisbursementRequest request) {
        String data = "LOAN_DISBURSEMENT:" + loanId;
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return String.valueOf(data.hashCode());
        }
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<DisbursementQueueItemResponse> getQueue(int page, int size) {
        getAuthenticatedAdmin();

        if (size > 50) size = 50;

        List<Loan> approvedLoans = loanRepository.findByStatus("APPROVED");

        List<DisbursementQueueItemResponse> queueItems = new ArrayList<>();
        for (Loan loan : approvedLoans) {
            Optional<Disbursement> disbOpt = disbursementRepository.findByLoanId(loan.getId());
            if (disbOpt.isPresent() && "DISBURSED".equalsIgnoreCase(disbOpt.get().getStatus())) {
                continue; // Exclude already disbursed loans
            }

            DisbursementQueueItemResponse item = new DisbursementQueueItemResponse();
            item.setLoanId(loan.getId());
            item.setApplicationId(loan.getApplication().getId());
            item.setCustomerName(loan.getCustomer().getFirstName() + " " + loan.getCustomer().getLastName());
            item.setProductName(loan.getProduct().getName());
            item.setPrincipalAmount(loan.getPrincipalAmount());
            item.setTermMonths(loan.getTermMonths());
            item.setLoanStatus(loan.getStatus());
            item.setDisbursementStatus(disbOpt.map(Disbursement::getStatus).orElse("PENDING_REQUEST"));
            item.setApprovedAt(loan.getCreatedAt());

            queueItems.add(item);
        }

        int totalElements = queueItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<DisbursementQueueItemResponse> pageContent = queueItems.subList(fromIndex, toIndex);

        return new PaginatedResponse<>(
                pageContent,
                page,
                size,
                totalElements,
                totalPages,
                page == 0,
                page >= totalPages - 1
        );
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getDisbursementById(Long disbursementId) {
        Disbursement disbursement = disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement not found"));

        List<DisbursementAttempt> attempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(disbursement.getId());
        return mapToResponse(disbursement, attempts.size());
    }

    @Transactional(readOnly = true)
    public List<DisbursementAttemptResponse> getDisbursementAttempts(Long disbursementId) {
        disbursementRepository.findById(disbursementId)
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement not found"));

        List<DisbursementAttempt> attempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(disbursementId);
        return attempts.stream()
                .map(this::mapToAttemptResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public DisbursementResponse getCustomerLoanDisbursement(Long applicationId) {
        CustomerProfile profile = getAuthenticatedCustomerProfile();

        LoanApplication app = loanApplicationRepository.findByIdAndCustomerId(applicationId, profile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        Loan loan = loanRepository.findByApplicationId(app.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan record not found for application"));

        Disbursement disbursement = disbursementRepository.findByLoanId(loan.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Disbursement record not found for loan"));

        List<DisbursementAttempt> attempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(disbursement.getId());
        return mapToResponse(disbursement, attempts.size());
    }

    @Transactional
    public DisbursementResponse initiateDisbursement(Long loanId, InitiateDisbursementRequest request, String idempotencyKey, String clientIp) {
        return initiateDisbursement(loanId, request, idempotencyKey, clientIp, null, null);
    }

    @Transactional
    public DisbursementResponse initiateDisbursement(Long loanId, InitiateDisbursementRequest request, String idempotencyKey, String clientIp, String testSimulateFailureHeader, String testFailureReasonHeader) {
        User adminUser = getAuthenticatedAdmin();

        String currentHash = calculateRequestHash(loanId, request);

        // Durable database-backed idempotency check
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            Optional<DisbursementIdempotencyKey> existingKeyOpt = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey.trim());
            if (existingKeyOpt.isPresent()) {
                DisbursementIdempotencyKey existingKey = existingKeyOpt.get();
                if (!existingKey.getRequestHash().equals(currentHash)) {
                    throw new ResourceConflictException("Idempotency key reused with different request payload");
                }
                if (existingKey.getResponsePayload() != null && !existingKey.getResponsePayload().isBlank()) {
                    try {
                        return objectMapper.readValue(existingKey.getResponsePayload(), DisbursementResponse.class);
                    } catch (JsonProcessingException e) {
                        // Fall back to mapping from entity
                    }
                }
                if (existingKey.getDisbursement() != null) {
                    List<DisbursementAttempt> attempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(existingKey.getDisbursement().getId());
                    return mapToResponse(existingKey.getDisbursement(), attempts.size());
                }
            }
        }

        // Pessimistic write lock on loan row to prevent concurrent duplicate disbursements
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found"));

        if ("DISBURSED".equalsIgnoreCase(loan.getStatus())) {
            Optional<Disbursement> existingDisb = disbursementRepository.findByLoanId(loan.getId());
            if (existingDisb.isPresent()) {
                List<DisbursementAttempt> attempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(existingDisb.get().getId());
                return mapToResponse(existingDisb.get(), attempts.size());
            }
            throw new ResourceConflictException("Loan has already been successfully disbursed");
        }

        if (!"APPROVED".equalsIgnoreCase(loan.getStatus())) {
            throw new ResourceConflictException("Loan status does not permit disbursement");
        }

        Disbursement disbursement = disbursementRepository.findByLoanId(loan.getId())
                .orElseGet(() -> {
                    Disbursement newDisb = new Disbursement();
                    newDisb.setLoan(loan);
                    newDisb.setAmount(loan.getPrincipalAmount());
                    newDisb.setStatus("PENDING");
                    return disbursementRepository.save(newDisb);
                });

        if ("DISBURSED".equalsIgnoreCase(disbursement.getStatus())) {
            throw new ResourceConflictException("Loan has already been successfully disbursed");
        }

        List<DisbursementAttempt> existingAttempts = attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(disbursement.getId());
        if (existingAttempts.size() >= MAX_RETRY_ATTEMPTS) {
            throw new ResourceConflictException("Maximum disbursement retry limit (" + MAX_RETRY_ATTEMPTS + ") exceeded");
        }

        // Execute deterministic processor
        DisbursementProcessor.SimulationResult result = disbursementProcessor.simulateDisbursement(
                disbursement, request, testSimulateFailureHeader, testFailureReasonHeader);

        int nextAttemptNumber = existingAttempts.size() + 1;
        DisbursementAttempt attempt = new DisbursementAttempt();
        attempt.setDisbursement(disbursement);
        attempt.setAttemptNumber(nextAttemptNumber);
        attempt.setStartedAt(LocalDateTime.now());

        if (result.isSuccess()) {
            attempt.setStatus("SUCCESS");
            attempt.setCompletedAt(LocalDateTime.now());

            disbursement.setStatus("DISBURSED");
            disbursement.setDisbursedAt(LocalDateTime.now());

            loan.setStatus("DISBURSED");

            // Generate and persist repayment schedule for the disbursed loan
            repaymentScheduleService.generateAndSaveSchedule(loan);
        } else {
            attempt.setStatus("FAILED");
            attempt.setFailureReason(result.getFailureReason());
            attempt.setCompletedAt(LocalDateTime.now());

            disbursement.setStatus("FAILED");
        }

        DisbursementAttempt savedAttempt = attemptRepository.save(attempt);
        Disbursement savedDisbursement = disbursementRepository.save(disbursement);
        loanRepository.save(loan);

        // AuditLog & OutboxEvent
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("disbursementId", savedDisbursement.getId());
        payloadMap.put("loanId", loan.getId());
        payloadMap.put("attemptId", savedAttempt.getId());
        payloadMap.put("attemptNumber", nextAttemptNumber);
        payloadMap.put("status", savedAttempt.getStatus());
        payloadMap.put("amount", savedDisbursement.getAmount());
        payloadMap.put("adminId", adminUser.getId());
        if (!result.isSuccess()) {
            payloadMap.put("failureReason", result.getFailureReason());
        }

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize disbursement audit/outbox payload", e);
        }

        auditService.recordAudit(result.isSuccess() ? "LOAN_DISBURSED" : "DISBURSEMENT_FAILED", "Loan", loan.getId(), null, jsonPayload, clientIp);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("Loan");
        outboxEvent.setAggregateId(loan.getId());
        outboxEvent.setType(result.isSuccess() ? "LOAN_DISBURSED" : "DISBURSEMENT_FAILED");
        outboxEvent.setPayload(jsonPayload);
        outboxEvent.setStatus("PENDING");
        outboxEventRepository.save(outboxEvent);

        DisbursementResponse response = mapToResponse(savedDisbursement, existingAttempts.size() + 1);

        // Persist database-backed idempotency record
        if (idempotencyKey != null && !idempotencyKey.trim().isEmpty()) {
            DisbursementIdempotencyKey dik = new DisbursementIdempotencyKey();
            dik.setIdempotencyKey(idempotencyKey.trim());
            dik.setRequestHash(currentHash);
            dik.setLoan(loan);
            dik.setDisbursement(savedDisbursement);
            try {
                dik.setResponsePayload(objectMapper.writeValueAsString(response));
            } catch (JsonProcessingException e) {
                // optional fallback
            }
            try {
                idempotencyKeyRepository.save(dik);
            } catch (DataIntegrityViolationException e) {
                // If a concurrent thread saved the same key first
                Optional<DisbursementIdempotencyKey> dikOpt = idempotencyKeyRepository.findByIdempotencyKey(idempotencyKey.trim());
                if (dikOpt.isPresent() && !dikOpt.get().getRequestHash().equals(currentHash)) {
                    throw new ResourceConflictException("Idempotency key reused with different request payload");
                }
            }
        }

        return response;
    }

    private DisbursementResponse mapToResponse(Disbursement disbursement, int attemptsCount) {
        DisbursementResponse response = new DisbursementResponse();
        response.setId(disbursement.getId());
        response.setLoanId(disbursement.getLoan().getId());
        response.setAmount(disbursement.getAmount());
        response.setStatus(disbursement.getStatus());
        response.setIsDemo(true);
        response.setDemoDisclaimer(DEMO_DISBURSEMENT_DISCLAIMER);
        response.setDisbursedAt(disbursement.getDisbursedAt());
        response.setCreatedAt(disbursement.getCreatedAt());
        response.setAttemptsCount(attemptsCount);
        return response;
    }

    private DisbursementAttemptResponse mapToAttemptResponse(DisbursementAttempt attempt) {
        DisbursementAttemptResponse response = new DisbursementAttemptResponse();
        response.setId(attempt.getId());
        response.setDisbursementId(attempt.getDisbursement().getId());
        response.setAttemptNumber(attempt.getAttemptNumber());
        response.setStatus(attempt.getStatus());
        response.setFailureReason(attempt.getFailureReason());
        response.setIsDemo(true);
        response.setStartedAt(attempt.getStartedAt());
        response.setCompletedAt(attempt.getCompletedAt());
        return response;
    }
}

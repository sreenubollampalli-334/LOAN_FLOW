package com.loanflow.loan.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.dto.CreateLoanApplicationRequest;
import com.loanflow.loan.dto.LoanApplicationResponse;
import com.loanflow.loan.dto.LoanApplicationSummaryResponse;
import com.loanflow.loan.entity.ApplicationStatusHistory;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.ApplicationStatusHistoryRepository;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanApplicationService {

    private final LoanApplicationRepository loanApplicationRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final LoanProductRepository loanProductRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final ObjectMapper objectMapper;

    public LoanApplicationService(LoanApplicationRepository loanApplicationRepository,
                                  ApplicationStatusHistoryRepository statusHistoryRepository,
                                  CustomerProfileRepository customerProfileRepository,
                                  LoanProductRepository loanProductRepository,
                                  UserRepository userRepository,
                                  AuditService auditService,
                                  ObjectMapper objectMapper) {
        this.loanApplicationRepository = loanApplicationRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.loanProductRepository = loanProductRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.objectMapper = objectMapper;
    }

    private User getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        User user = getAuthenticatedUser();
        return customerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    @Transactional
    public LoanApplicationResponse createDraft(CreateLoanApplicationRequest request) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
        User user = customerProfile.getUser();

        LoanProduct product = loanProductRepository.findByIdAndIsActiveTrue(request.getProductId())
                .orElseThrow(() -> new ResourceNotFoundException("Active loan product not found with id: " + request.getProductId()));

        if (request.getRequestedAmount().compareTo(product.getMinAmount()) < 0 ||
            request.getRequestedAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new IllegalArgumentException("Requested amount must be between " + product.getMinAmount() + " and " + product.getMaxAmount());
        }

        if (request.getTermMonths() < product.getMinTermMonths() ||
            request.getTermMonths() > product.getMaxTermMonths()) {
            throw new IllegalArgumentException("Requested term must be between " + product.getMinTermMonths() + " and " + product.getMaxTermMonths() + " months");
        }

        LoanApplication application = new LoanApplication();
        application.setCustomer(customerProfile);
        application.setProduct(product);
        application.setRequestedAmount(request.getRequestedAmount());
        application.setTermMonths(request.getTermMonths());
        application.setPurpose(request.getPurpose());
        application.setStatus(ApplicationStatus.DRAFT.name());

        LoanApplication savedApplication = loanApplicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(savedApplication);
        history.setNewStatus(ApplicationStatus.DRAFT.name());
        history.setActor(user);
        history.setReason("Draft created by customer");

        statusHistoryRepository.save(history);

        String jsonPayload;
        try {
            java.util.Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
            payloadMap.put("status", savedApplication.getStatus());
            payloadMap.put("requestedAmount", savedApplication.getRequestedAmount());
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            jsonPayload = "{}";
        }
        auditService.recordAudit("APPLICATION_CREATED", "LoanApplication", savedApplication.getId(), null, jsonPayload, "system");

        return mapToResponse(savedApplication);
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<LoanApplicationSummaryResponse> getMyApplications(int page, int size) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<LoanApplication> applicationPage = loanApplicationRepository.findByCustomerId(customerProfile.getId(), pageable);

        List<LoanApplicationSummaryResponse> content = applicationPage.getContent().stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                content,
                applicationPage.getNumber(),
                applicationPage.getSize(),
                applicationPage.getTotalElements(),
                applicationPage.getTotalPages(),
                applicationPage.isFirst(),
                applicationPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public LoanApplicationResponse getMyApplicationById(Long id) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(id, customerProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        return mapToResponse(application);
    }

    @Transactional
    public LoanApplicationResponse updateDraft(Long id, com.loanflow.loan.dto.UpdateLoanApplicationRequest request) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(id, customerProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.DRAFT.name().equals(application.getStatus()) &&
            !ApplicationStatus.RETURNED_FOR_REVIEW.name().equals(application.getStatus())) {
            throw new com.loanflow.exception.ResourceConflictException("Only DRAFT or RETURNED_FOR_REVIEW applications can be updated");
        }

        LoanProduct product = application.getProduct();
        if (request.getRequestedAmount().compareTo(product.getMinAmount()) < 0 ||
            request.getRequestedAmount().compareTo(product.getMaxAmount()) > 0) {
            throw new IllegalArgumentException("Requested amount must be between " + product.getMinAmount() + " and " + product.getMaxAmount());
        }

        if (request.getTermMonths() < product.getMinTermMonths() ||
            request.getTermMonths() > product.getMaxTermMonths()) {
            throw new IllegalArgumentException("Requested term must be between " + product.getMinTermMonths() + " and " + product.getMaxTermMonths() + " months");
        }

        application.setRequestedAmount(request.getRequestedAmount());
        application.setTermMonths(request.getTermMonths());
        application.setPurpose(request.getPurpose());

        LoanApplication updatedApplication = loanApplicationRepository.save(application);
        return mapToResponse(updatedApplication);
    }

    @Transactional
    public LoanApplicationResponse submitApplication(Long id) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
        User user = customerProfile.getUser();

        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(id, customerProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.DRAFT.name().equals(application.getStatus()) &&
            !ApplicationStatus.RETURNED_FOR_REVIEW.name().equals(application.getStatus())) {
            throw new com.loanflow.exception.ResourceConflictException("Only DRAFT or RETURNED_FOR_REVIEW applications can be submitted");
        }

        String previousStatus = application.getStatus();
        application.setStatus(ApplicationStatus.SUBMITTED.name());

        LoanApplication savedApplication = loanApplicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(savedApplication);
        history.setPreviousStatus(previousStatus);
        history.setNewStatus(ApplicationStatus.SUBMITTED.name());
        history.setActor(user);
        history.setReason("Submitted by customer");

        statusHistoryRepository.save(history);

        String jsonPayload;
        try {
            java.util.Map<String, Object> payloadMap = new java.util.LinkedHashMap<>();
            payloadMap.put("previousStatus", previousStatus);
            payloadMap.put("newStatus", savedApplication.getStatus());
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            jsonPayload = "{}";
        }
        auditService.recordAudit("APPLICATION_SUBMITTED", "LoanApplication", savedApplication.getId(), null, jsonPayload, "system");

        return mapToResponse(savedApplication);
    }

    private LoanApplicationResponse mapToResponse(LoanApplication application) {
        LoanApplicationResponse response = new LoanApplicationResponse();
        response.setId(application.getId());
        response.setProductId(application.getProduct().getId());
        response.setProductName(application.getProduct().getName());
        response.setRequestedAmount(application.getRequestedAmount());
        response.setApprovedAmount(application.getApprovedAmount());
        response.setTermMonths(application.getTermMonths());
        response.setStatus(application.getStatus());
        response.setPurpose(application.getPurpose());
        response.setCreatedAt(application.getCreatedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }

    private LoanApplicationSummaryResponse mapToSummaryResponse(LoanApplication application) {
        LoanApplicationSummaryResponse response = new LoanApplicationSummaryResponse();
        response.setId(application.getId());
        response.setProductId(application.getProduct().getId());
        response.setProductName(application.getProduct().getName());
        response.setRequestedAmount(application.getRequestedAmount());
        response.setTermMonths(application.getTermMonths());
        response.setStatus(application.getStatus());
        response.setCreatedAt(application.getCreatedAt());
        response.setUpdatedAt(application.getUpdatedAt());
        return response;
    }
}

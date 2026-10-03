package com.loanflow.review.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.document.entity.Document;
import com.loanflow.document.entity.DocumentReview;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.repository.DocumentReviewRepository;
import com.loanflow.dto.PaginatedResponse;
import com.loanflow.eligibility.dto.ReasonCodeResponse;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.entity.EligibilityReasonCode;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.eligibility.repository.EligibilityReasonCodeRepository;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.ApplicationStatusHistory;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.ApplicationStatusHistoryRepository;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.review.dto.*;
import com.loanflow.review.entity.CreditReview;
import com.loanflow.review.entity.ManagerReview;
import com.loanflow.review.repository.CreditReviewRepository;
import com.loanflow.review.repository.ManagerReviewRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class ManagerReviewService {

    public static final String DECISION_APPROVE = "APPROVE";
    public static final String DECISION_REJECT = "REJECT";
    public static final String DECISION_RETURN_FOR_REVIEW = "RETURN_FOR_REVIEW";

    private final ManagerReviewRepository managerReviewRepository;
    private final CreditReviewRepository creditReviewRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final LoanRepository loanRepository;
    private final ApplicationStatusHistoryRepository statusHistoryRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final EligibilityReasonCodeRepository eligibilityReasonCodeRepository;
    private final ProductRequiredDocumentRepository requiredDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentReviewRepository documentReviewRepository;
    private final UserRepository userRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;

    public ManagerReviewService(ManagerReviewRepository managerReviewRepository,
                                CreditReviewRepository creditReviewRepository,
                                LoanApplicationRepository loanApplicationRepository,
                                LoanRepository loanRepository,
                                ApplicationStatusHistoryRepository statusHistoryRepository,
                                EligibilityAssessmentRepository eligibilityAssessmentRepository,
                                EligibilityReasonCodeRepository eligibilityReasonCodeRepository,
                                ProductRequiredDocumentRepository requiredDocumentRepository,
                                DocumentRepository documentRepository,
                                DocumentReviewRepository documentReviewRepository,
                                UserRepository userRepository,
                                AuditService auditService,
                                OutboxEventRepository outboxEventRepository,
                                ObjectMapper objectMapper) {
        this.managerReviewRepository = managerReviewRepository;
        this.creditReviewRepository = creditReviewRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.loanRepository = loanRepository;
        this.statusHistoryRepository = statusHistoryRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.eligibilityReasonCodeRepository = eligibilityReasonCodeRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.documentRepository = documentRepository;
        this.documentReviewRepository = documentReviewRepository;
        this.userRepository = userRepository;
        this.auditService = auditService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
    }

    private User getAuthenticatedManager() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            boolean isManager = user.getRoles().stream()
                    .anyMatch(r -> "MANAGER".equalsIgnoreCase(r.getName()) || "ROLE_MANAGER".equalsIgnoreCase(r.getName()));

            if (!isManager) {
                throw new AccessDeniedException("Access denied: Manager role required");
            }
            return user;
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<ManagerReviewQueueItemResponse> getQueue(int page, int size) {
        getAuthenticatedManager();

        if (size > 50) size = 50;

        List<LoanApplication> submittedApps = loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name());

        List<ManagerReviewQueueItemResponse> queueItems = new ArrayList<>();
        for (LoanApplication app : submittedApps) {
            List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(app.getId());
            if (assessments.isEmpty()) {
                continue; // Prerequisite: must have completed eligibility assessment
            }

            List<CreditReview> creditReviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(app.getId());
            if (creditReviews.isEmpty()) {
                continue; // Prerequisite: must have credit officer review
            }

            List<ManagerReview> managerReviews = managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(app.getId());
            if (!managerReviews.isEmpty()) {
                ManagerReview latestManagerReview = managerReviews.get(0);
                if (!DECISION_RETURN_FOR_REVIEW.equals(latestManagerReview.getDecision())) {
                    continue; // Exclude applications already reviewed with a final decision
                }
                
                CreditReview latestCreditReview = creditReviews.get(0);
                if (!latestCreditReview.getReviewedAt().isAfter(latestManagerReview.getReviewedAt())) {
                    continue; // Wait for credit officer to review the resubmitted application
                }
            }

            CreditReview creditReview = creditReviews.get(0);
            if (CreditReviewService.DECISION_REQUEST_INFO.equals(creditReview.getDecision())) {
                continue; // Manager should not see applications requesting customer information
            }

            ManagerReviewQueueItemResponse item = new ManagerReviewQueueItemResponse();
            item.setApplicationId(app.getId());
            item.setProductName(app.getProduct().getName());
            item.setRequestedAmount(app.getRequestedAmount());
            item.setTermMonths(app.getTermMonths());
            item.setSubmittedAt(app.getUpdatedAt());
            item.setEligibilityDecision(assessments.get(0).getDecision());
            item.setCreditOfficerRecommendation(creditReview.getDecision());
            item.setCreditOfficerComments(creditReview.getComments());
            item.setHasExistingManagerReview(false);

            queueItems.add(item);
        }

        int totalElements = queueItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<ManagerReviewQueueItemResponse> pageContent = queueItems.subList(fromIndex, toIndex);

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
    public ManagerReviewDetailsResponse getReviewDetails(Long applicationId) {
        getAuthenticatedManager();

        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        ManagerReviewDetailsResponse response = new ManagerReviewDetailsResponse();

        // Application Summary
        CreditReviewDetailsResponse.ApplicationSummary appSummary = new CreditReviewDetailsResponse.ApplicationSummary();
        appSummary.setId(application.getId());
        appSummary.setStatus(application.getStatus());
        appSummary.setRequestedAmount(application.getRequestedAmount());
        appSummary.setTermMonths(application.getTermMonths());
        appSummary.setPurpose(application.getPurpose());
        appSummary.setCreatedAt(application.getCreatedAt());
        response.setApplication(appSummary);

        // Customer Summary
        CustomerProfile profile = application.getCustomer();
        CreditReviewDetailsResponse.CustomerSummary custSummary = new CreditReviewDetailsResponse.CustomerSummary();
        custSummary.setId(profile.getId());
        custSummary.setFirstName(profile.getFirstName());
        custSummary.setLastName(profile.getLastName());
        custSummary.setEmail(profile.getUser().getEmail());
        response.setCustomer(custSummary);

        // Product Summary
        LoanProduct product = application.getProduct();
        CreditReviewDetailsResponse.ProductSummary prodSummary = new CreditReviewDetailsResponse.ProductSummary();
        prodSummary.setId(product.getId());
        prodSummary.setName(product.getName());
        prodSummary.setMinAmount(product.getMinAmount());
        prodSummary.setMaxAmount(product.getMaxAmount());
        prodSummary.setMinTermMonths(product.getMinTermMonths());
        prodSummary.setMaxTermMonths(product.getMaxTermMonths());
        prodSummary.setInterestRate(product.getInterestRate());
        response.setProduct(prodSummary);

        // Eligibility Summary
        List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(application.getId());
        if (!assessments.isEmpty()) {
            EligibilityAssessment assessment = assessments.get(0);
            List<EligibilityReasonCode> reasonCodes = eligibilityReasonCodeRepository.findByAssessmentId(assessment.getId());

            CreditReviewDetailsResponse.EligibilitySummary eligSummary = new CreditReviewDetailsResponse.EligibilitySummary();
            eligSummary.setId(assessment.getId());
            eligSummary.setDecision(assessment.getDecision());
            eligSummary.setRulesetVersion(assessment.getRulesetVersion());
            eligSummary.setAssessedAt(assessment.getAssessedAt());
            eligSummary.setReasons(reasonCodes.stream()
                    .map(r -> new ReasonCodeResponse(r.getCode(), r.getDescription()))
                    .collect(Collectors.toList()));
            response.setEligibilityAssessment(eligSummary);
        }

        // Credit Officer Review Summary
        List<CreditReview> creditReviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!creditReviews.isEmpty()) {
            CreditReview cr = creditReviews.get(0);
            CreditReviewResponse crResp = new CreditReviewResponse();
            crResp.setId(cr.getId());
            crResp.setApplicationId(application.getId());
            crResp.setCreditOfficerId(cr.getCreditOfficer().getId());
            crResp.setCreditOfficerEmail(cr.getCreditOfficer().getEmail());
            crResp.setDecision(cr.getDecision());
            crResp.setComments(cr.getComments());
            crResp.setReviewedAt(cr.getReviewedAt());
            response.setCreditOfficerReview(crResp);
        }

        // Documents Summary
        List<ProductRequiredDocument> requiredDocs = requiredDocumentRepository.findByProductId(product.getId());
        List<Document> uploadedDocs = documentRepository.findByApplicationId(application.getId());
        uploadedDocs.sort(Comparator.comparing(Document::getCreatedAt).reversed());

        Map<String, Document> uploadedByType = uploadedDocs.stream()
                .collect(Collectors.toMap(Document::getDocumentType, d -> d, (d1, d2) -> d1));

        List<CreditReviewDetailsResponse.DocumentSummary> docSummaries = new ArrayList<>();
        for (ProductRequiredDocument reqDoc : requiredDocs) {
            CreditReviewDetailsResponse.DocumentSummary docSummary = new CreditReviewDetailsResponse.DocumentSummary();
            docSummary.setDocumentType(reqDoc.getDocumentType());
            docSummary.setIsMandatory(reqDoc.getIsMandatory());

            Document uploadedDoc = uploadedByType.get(reqDoc.getDocumentType());
            if (uploadedDoc != null) {
                docSummary.setId(uploadedDoc.getId());
                docSummary.setOriginalFilename(uploadedDoc.getOriginalFilename());
                docSummary.setContentType(uploadedDoc.getContentType());
                docSummary.setFileSize(uploadedDoc.getFileSize());
                docSummary.setUploadStatus(uploadedDoc.getUploadStatus());
                docSummary.setUploadedAt(uploadedDoc.getCreatedAt());

                List<DocumentReview> reviews = documentReviewRepository.findByDocumentIdOrderByReviewedAtDesc(uploadedDoc.getId());
                docSummary.setReviewStatus(reviews.isEmpty() ? "PENDING" : reviews.get(0).getStatus());
            } else {
                docSummary.setUploadStatus("NOT_UPLOADED");
                docSummary.setReviewStatus("MISSING");
            }
            docSummaries.add(docSummary);
        }
        response.setDocuments(docSummaries);

        // Existing Manager Review Summary
        List<ManagerReview> managerReviews = managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!managerReviews.isEmpty()) {
            ManagerReview mr = managerReviews.get(0);
            ManagerReviewResponse mrResp = new ManagerReviewResponse();
            mrResp.setId(mr.getId());
            mrResp.setApplicationId(application.getId());
            mrResp.setManagerId(mr.getManager().getId());
            mrResp.setManagerEmail(mr.getManager().getEmail());
            mrResp.setDecision(mr.getDecision());
            mrResp.setComments(mr.getComments());
            mrResp.setApplicationStatus(application.getStatus());
            mrResp.setReviewedAt(mr.getReviewedAt());

            Optional<Loan> loanOpt = loanRepository.findByApplicationId(application.getId());
            loanOpt.ifPresent(loan -> mrResp.setLoanId(loan.getId()));

            response.setExistingManagerReview(mrResp);
        }

        return response;
    }

    @Transactional
    public ManagerReviewResponse createReview(Long applicationId, CreateManagerReviewRequest request, String clientIp) {
        User managerUser = getAuthenticatedManager();

        LoanApplication application = loanApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.SUBMITTED.name().equals(application.getStatus())) {
            throw new ResourceConflictException("Application must be in SUBMITTED status to perform manager review");
        }

        List<CreditReview> creditReviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (creditReviews.isEmpty()) {
            throw new ResourceConflictException("Application must have a completed credit officer review before manager review");
        }

        List<ManagerReview> existingManagerReviews = managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!existingManagerReviews.isEmpty()) {
            ManagerReview latestManagerReview = existingManagerReviews.get(0);
            if (!DECISION_RETURN_FOR_REVIEW.equals(latestManagerReview.getDecision())) {
                throw new ResourceConflictException("Loan application has already been reviewed by a manager");
            }
            
            CreditReview latestCreditReview = creditReviews.get(0);
            if (!latestCreditReview.getReviewedAt().isAfter(latestManagerReview.getReviewedAt())) {
                throw new ResourceConflictException("Application must have a new credit officer review before another manager review");
            }
        }

        List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(application.getId());
        if (assessments.isEmpty()) {
            throw new ResourceConflictException("Application must have a completed eligibility assessment before manager review");
        }

        EligibilityAssessment latestAssessment = assessments.get(0);
        String eligDecision = latestAssessment.getDecision();

        // Enforce decision rules: APPROVE requires ELIGIBLE
        if (DECISION_APPROVE.equalsIgnoreCase(request.getDecision())) {
            if ("INELIGIBLE".equalsIgnoreCase(eligDecision)) {
                throw new ResourceConflictException("Cannot APPROVE an application that failed demo eligibility checks (INELIGIBLE)");
            }
            if ("INCOMPLETE".equalsIgnoreCase(eligDecision)) {
                throw new ResourceConflictException("Cannot APPROVE an application with incomplete eligibility requirements (INCOMPLETE)");
            }
        }

        // Save ManagerReview
        ManagerReview review = new ManagerReview();
        review.setApplication(application);
        review.setManager(managerUser);
        review.setDecision(request.getDecision());
        review.setComments(request.getComments());
        review.setReviewedAt(LocalDateTime.now());

        ManagerReview savedReview = managerReviewRepository.save(review);

        // Update Application Status & History
        String prevStatus = application.getStatus();
        String newStatusStr;
        if (DECISION_APPROVE.equalsIgnoreCase(request.getDecision())) {
            newStatusStr = ApplicationStatus.APPROVED.name();
            if (application.getApprovedAmount() == null) {
                application.setApprovedAmount(application.getRequestedAmount());
            }
        } else if (DECISION_REJECT.equalsIgnoreCase(request.getDecision())) {
            newStatusStr = ApplicationStatus.REJECTED.name();
        } else {
            newStatusStr = ApplicationStatus.RETURNED_FOR_REVIEW.name();
        }

        application.setStatus(newStatusStr);
        loanApplicationRepository.save(application);

        ApplicationStatusHistory history = new ApplicationStatusHistory();
        history.setApplication(application);
        history.setPreviousStatus(prevStatus);
        history.setNewStatus(newStatusStr);
        history.setReason(request.getComments());
        history.setActor(managerUser);
        statusHistoryRepository.save(history);

        // Create Demo Loan record if APPROVED
        Long loanId = null;
        if (DECISION_APPROVE.equalsIgnoreCase(request.getDecision())) {
            Optional<Loan> existingLoan = loanRepository.findByApplicationId(application.getId());
            if (existingLoan.isEmpty()) {
                Loan loan = new Loan();
                loan.setApplication(application);
                loan.setCustomer(application.getCustomer());
                loan.setProduct(application.getProduct());
                loan.setPrincipalAmount(application.getApprovedAmount() != null ? application.getApprovedAmount() : application.getRequestedAmount());
                loan.setInterestRate(application.getProduct().getInterestRate());
                loan.setInterestCalculationMethod("FLAT");
                loan.setTermMonths(application.getTermMonths());
                loan.setStatus("APPROVED");

                Loan savedLoan = loanRepository.save(loan);
                loanId = savedLoan.getId();
            } else {
                loanId = existingLoan.get().getId();
            }
        }

        // AuditLog & OutboxEvent
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("managerReviewId", savedReview.getId());
        payloadMap.put("applicationId", application.getId());
        payloadMap.put("managerId", managerUser.getId());
        payloadMap.put("decision", savedReview.getDecision());
        payloadMap.put("newApplicationStatus", newStatusStr);
        payloadMap.put("loanId", loanId);
        payloadMap.put("comments", savedReview.getComments());
        payloadMap.put("reviewedAt", savedReview.getReviewedAt().toString());

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize manager audit/outbox payload", e);
        }

        auditService.recordAudit("MANAGER_REVIEW_SUBMITTED", "LoanApplication", application.getId(), null, jsonPayload, clientIp);

        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("LoanApplication");
        outboxEvent.setAggregateId(application.getId());
        outboxEvent.setType("MANAGER_REVIEW_SUBMITTED");
        outboxEvent.setPayload(jsonPayload);
        outboxEvent.setStatus("PENDING");
        outboxEventRepository.save(outboxEvent);

        ManagerReviewResponse response = new ManagerReviewResponse();
        response.setId(savedReview.getId());
        response.setApplicationId(application.getId());
        response.setManagerId(managerUser.getId());
        response.setManagerEmail(managerUser.getEmail());
        response.setDecision(savedReview.getDecision());
        response.setComments(savedReview.getComments());
        response.setApplicationStatus(newStatusStr);
        response.setLoanId(loanId);
        response.setReviewedAt(savedReview.getReviewedAt());

        return response;
    }
}

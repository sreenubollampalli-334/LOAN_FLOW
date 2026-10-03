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
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.review.dto.CreateCreditReviewRequest;
import com.loanflow.review.dto.CreditReviewDetailsResponse;
import com.loanflow.review.dto.CreditReviewQueueItemResponse;
import com.loanflow.review.dto.CreditReviewResponse;
import com.loanflow.review.entity.CreditReview;
import com.loanflow.review.repository.CreditReviewRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class CreditReviewService {

    public static final String DECISION_RECOMMEND = "RECOMMEND";
    public static final String DECISION_DO_NOT_RECOMMEND = "DO_NOT_RECOMMEND";
    public static final String DECISION_REQUEST_INFO = "REQUEST_INFORMATION";

    private final CreditReviewRepository creditReviewRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final EligibilityAssessmentRepository eligibilityAssessmentRepository;
    private final EligibilityReasonCodeRepository eligibilityReasonCodeRepository;
    private final ProductRequiredDocumentRepository requiredDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentReviewRepository documentReviewRepository;
    private final UserRepository userRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final AuditService auditService;
    private final OutboxEventRepository outboxEventRepository;
    private final ObjectMapper objectMapper;
    private final com.loanflow.loan.repository.ApplicationStatusHistoryRepository statusHistoryRepository;

    public CreditReviewService(CreditReviewRepository creditReviewRepository,
                               LoanApplicationRepository loanApplicationRepository,
                               EligibilityAssessmentRepository eligibilityAssessmentRepository,
                               EligibilityReasonCodeRepository eligibilityReasonCodeRepository,
                               ProductRequiredDocumentRepository requiredDocumentRepository,
                               DocumentRepository documentRepository,
                               DocumentReviewRepository documentReviewRepository,
                               UserRepository userRepository,
                               CustomerProfileRepository customerProfileRepository,
                               AuditService auditService,
                               OutboxEventRepository outboxEventRepository,
                               ObjectMapper objectMapper,
                               com.loanflow.loan.repository.ApplicationStatusHistoryRepository statusHistoryRepository) {
        this.creditReviewRepository = creditReviewRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.eligibilityAssessmentRepository = eligibilityAssessmentRepository;
        this.eligibilityReasonCodeRepository = eligibilityReasonCodeRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.documentRepository = documentRepository;
        this.documentReviewRepository = documentReviewRepository;
        this.userRepository = userRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.auditService = auditService;
        this.outboxEventRepository = outboxEventRepository;
        this.objectMapper = objectMapper;
        this.statusHistoryRepository = statusHistoryRepository;
    }

    private User getAuthenticatedCreditOfficer() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            User user = userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));

            boolean isCreditOfficer = user.getRoles().stream()
                    .anyMatch(r -> "CREDIT_OFFICER".equalsIgnoreCase(r.getName()) || "ROLE_CREDIT_OFFICER".equalsIgnoreCase(r.getName()));

            if (!isCreditOfficer) {
                throw new AccessDeniedException("Access denied: Credit Officer role required");
            }
            return user;
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<CreditReviewQueueItemResponse> getQueue(int page, int size) {
        getAuthenticatedCreditOfficer();

        if (size > 50) size = 50;

        List<LoanApplication> submittedApps = loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name());

        List<CreditReviewQueueItemResponse> queueItems = new ArrayList<>();
        for (LoanApplication app : submittedApps) {
            List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(app.getId());
            if (assessments.isEmpty()) {
                continue; // Prerequisite: must have completed eligibility assessment
            }

            List<CreditReview> reviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(app.getId());
            if (!reviews.isEmpty()) {
                CreditReview latestReview = reviews.get(0);
                boolean allowReview = DECISION_REQUEST_INFO.equals(latestReview.getDecision());
                
                if (!allowReview) {
                    List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId());
                    LocalDateTime latestSubmitTime = null;
                    for (ApplicationStatusHistory h : history) {
                        if (ApplicationStatus.SUBMITTED.name().equals(h.getNewStatus())) {
                            latestSubmitTime = h.getCreatedAt();
                            break;
                        }
                    }
                    if (latestSubmitTime != null && latestSubmitTime.isAfter(latestReview.getReviewedAt())) {
                        allowReview = true;
                    }
                }
                
                if (!allowReview) {
                    continue; // Exclude applications already reviewed with a final decision
                }
            }

            CreditReviewQueueItemResponse item = new CreditReviewQueueItemResponse();
            item.setApplicationId(app.getId());
            item.setProductName(app.getProduct().getName());
            item.setRequestedAmount(app.getRequestedAmount());
            item.setTermMonths(app.getTermMonths());
            item.setSubmittedAt(app.getUpdatedAt()); // update timestamp when submitted
            item.setEligibilityDecision(assessments.get(0).getDecision());
            item.setHasExistingReview(!reviews.isEmpty());

            queueItems.add(item);
        }

        // Apply in-memory pagination
        int totalElements = queueItems.size();
        int totalPages = (int) Math.ceil((double) totalElements / size);
        int fromIndex = Math.min(page * size, totalElements);
        int toIndex = Math.min(fromIndex + size, totalElements);

        List<CreditReviewQueueItemResponse> pageContent = queueItems.subList(fromIndex, toIndex);

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
    public CreditReviewDetailsResponse getReviewDetails(Long applicationId) {
        getAuthenticatedCreditOfficer();

        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        CreditReviewDetailsResponse response = new CreditReviewDetailsResponse();

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

        // Existing Credit Review Summary
        List<CreditReview> reviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!reviews.isEmpty()) {
            CreditReview review = reviews.get(0);
            CreditReviewResponse reviewResp = new CreditReviewResponse();
            reviewResp.setId(review.getId());
            reviewResp.setApplicationId(application.getId());
            reviewResp.setCreditOfficerId(review.getCreditOfficer().getId());
            reviewResp.setCreditOfficerEmail(review.getCreditOfficer().getEmail());
            reviewResp.setDecision(review.getDecision());
            reviewResp.setComments(review.getComments());
            reviewResp.setReviewedAt(review.getReviewedAt());
            response.setExistingReview(reviewResp);
        }

        return response;
    }

    @Transactional
    public CreditReviewResponse createReview(Long applicationId, CreateCreditReviewRequest request, String clientIp) {
        User officer = getAuthenticatedCreditOfficer();

        LoanApplication application = loanApplicationRepository.findByIdForUpdate(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.SUBMITTED.name().equals(application.getStatus())) {
            throw new ResourceConflictException("Only SUBMITTED applications can be reviewed");
        }

        List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(application.getId());
        if (assessments.isEmpty()) {
            throw new ResourceConflictException("Application must have a completed eligibility assessment before credit review");
        }

        List<CreditReview> existingReviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!existingReviews.isEmpty()) {
            CreditReview latestReview = existingReviews.get(0);
            boolean allowReview = DECISION_REQUEST_INFO.equals(latestReview.getDecision());
            
            if (!allowReview) {
                List<ApplicationStatusHistory> history = statusHistoryRepository.findByApplicationIdOrderByCreatedAtDesc(application.getId());
                LocalDateTime latestSubmitTime = null;
                for (ApplicationStatusHistory h : history) {
                    if (ApplicationStatus.SUBMITTED.name().equals(h.getNewStatus())) {
                        latestSubmitTime = h.getCreatedAt();
                        break;
                    }
                }
                if (latestSubmitTime != null && latestSubmitTime.isAfter(latestReview.getReviewedAt())) {
                    allowReview = true;
                }
            }
            
            if (!allowReview) {
                throw new ResourceConflictException("Loan application has already been reviewed with a final recommendation");
            }
        }

        // Rule check: Enforce eligibility checks against recommendation
        EligibilityAssessment latestAssessment = assessments.get(0);
        String eligDecision = latestAssessment.getDecision();

        if (DECISION_RECOMMEND.equalsIgnoreCase(request.getDecision())) {
            if ("INELIGIBLE".equalsIgnoreCase(eligDecision)) {
                throw new ResourceConflictException("Cannot RECOMMEND an application that failed demo eligibility checks (INELIGIBLE)");
            }
            if ("INCOMPLETE".equalsIgnoreCase(eligDecision)) {
                throw new ResourceConflictException("Cannot RECOMMEND an application with incomplete eligibility requirements (INCOMPLETE)");
            }
        }

        // Save CreditReview
        CreditReview review = new CreditReview();
        review.setApplication(application);
        review.setCreditOfficer(officer);
        review.setDecision(request.getDecision());
        review.setComments(request.getComments());
        review.setReviewedAt(LocalDateTime.now());

        CreditReview savedReview = creditReviewRepository.save(review);

        // Audit & Outbox payload map
        Map<String, Object> payloadMap = new LinkedHashMap<>();
        payloadMap.put("reviewId", savedReview.getId());
        payloadMap.put("applicationId", application.getId());
        payloadMap.put("creditOfficerId", officer.getId());
        payloadMap.put("decision", savedReview.getDecision());
        payloadMap.put("comments", savedReview.getComments());
        payloadMap.put("reviewedAt", savedReview.getReviewedAt().toString());

        String jsonPayload;
        try {
            jsonPayload = objectMapper.writeValueAsString(payloadMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize audit/outbox payload", e);
        }

        // Update Application Status if REQUEST_INFORMATION
        String prevStatus = application.getStatus();
        if (DECISION_REQUEST_INFO.equals(request.getDecision())) {
            application.setStatus(ApplicationStatus.DRAFT.name());
            loanApplicationRepository.save(application);
            
            ApplicationStatusHistory history = new ApplicationStatusHistory();
            history.setApplication(application);
            history.setPreviousStatus(prevStatus);
            history.setNewStatus(ApplicationStatus.DRAFT.name());
            history.setReason(request.getComments());
            history.setActor(officer);
            statusHistoryRepository.save(history);
        }

        // Create & save AuditLog
        auditService.recordAudit("CREDIT_REVIEW_SUBMITTED", "LoanApplication", application.getId(), null, jsonPayload, clientIp);

        // Create & save OutboxEvent
        OutboxEvent outboxEvent = new OutboxEvent();
        outboxEvent.setAggregateType("LoanApplication");
        outboxEvent.setAggregateId(application.getId());
        outboxEvent.setType("CREDIT_REVIEW_SUBMITTED");
        outboxEvent.setPayload(jsonPayload);
        outboxEvent.setStatus("PENDING");
        outboxEventRepository.save(outboxEvent);

        CreditReviewResponse response = new CreditReviewResponse();
        response.setId(savedReview.getId());
        response.setApplicationId(application.getId());
        response.setCreditOfficerId(officer.getId());
        response.setCreditOfficerEmail(officer.getEmail());
        response.setDecision(savedReview.getDecision());
        response.setComments(savedReview.getComments());
        response.setReviewedAt(savedReview.getReviewedAt());

        return response;
    }

    @Transactional
    public void reviewDocument(Long applicationId, Long documentId, com.loanflow.review.dto.DocumentReviewRequest request) {
        User officer = getAuthenticatedCreditOfficer();

        LoanApplication application = loanApplicationRepository.findById(applicationId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.SUBMITTED.name().equals(application.getStatus())) {
            throw new ResourceConflictException("Only SUBMITTED applications can have documents reviewed");
        }

        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new ResourceNotFoundException("Document not found"));

        if (!document.getApplication().getId().equals(application.getId())) {
            throw new ResourceNotFoundException("Document does not belong to this application");
        }

        List<DocumentReview> existingReviews = documentReviewRepository.findByDocumentIdOrderByReviewedAtDesc(document.getId());
        if (!existingReviews.isEmpty()) {
            throw new ResourceConflictException("Document has already been reviewed");
        }
        
        List<CreditReview> existingCreditReviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(application.getId());
        if (!existingCreditReviews.isEmpty()) {
            throw new ResourceConflictException("Cannot review documents after a final credit review decision has been made");
        }
        
        if ("REJECTED".equals(request.getStatus()) && (request.getComments() == null || request.getComments().trim().isEmpty())) {
            throw new IllegalArgumentException("Comments are required when rejecting a document");
        }

        DocumentReview review = new DocumentReview();
        review.setDocument(document);
        review.setReviewer(officer);
        review.setStatus(request.getStatus());
        review.setComments(request.getComments());
        review.setReviewedAt(LocalDateTime.now());

        documentReviewRepository.save(review);
    }
}

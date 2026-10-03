package com.loanflow.eligibility.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.document.entity.Document;
import com.loanflow.document.entity.DocumentReview;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.repository.DocumentReviewRepository;
import com.loanflow.eligibility.dto.EligibilityAssessmentResponse;
import com.loanflow.eligibility.dto.ReasonCodeResponse;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.entity.EligibilityReasonCode;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.eligibility.repository.EligibilityReasonCodeRepository;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.entity.ProductRequiredDocument;
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class EligibilityAssessmentService {

    public static final String DECISION_ELIGIBLE = "ELIGIBLE";
    public static final String DECISION_INELIGIBLE = "INELIGIBLE";
    public static final String DECISION_INCOMPLETE = "INCOMPLETE";

    public static final String CODE_AMOUNT_BELOW_MIN = "AMOUNT_BELOW_PRODUCT_MIN";
    public static final String CODE_AMOUNT_ABOVE_MAX = "AMOUNT_ABOVE_PRODUCT_MAX";
    public static final String CODE_TENURE_BELOW_MIN = "TENURE_BELOW_PRODUCT_MIN";
    public static final String CODE_TENURE_ABOVE_MAX = "TENURE_ABOVE_PRODUCT_MAX";
    public static final String CODE_DOC_MISSING = "REQUIRED_DOCUMENT_MISSING";
    public static final String CODE_DOC_PENDING = "REQUIRED_DOCUMENT_PENDING";
    public static final String CODE_DOC_REJECTED = "REQUIRED_DOCUMENT_REJECTED";

    public static final String DEMO_PASSED_DISCLAIMER = "Demo eligibility checks passed. This is an educational assessment and is not a loan approval or offer.";
    public static final String DEMO_GENERAL_DISCLAIMER = "Educational demo assessment, not a loan approval or credit decision.";

    private final EligibilityAssessmentRepository assessmentRepository;
    private final EligibilityReasonCodeRepository reasonCodeRepository;
    private final LoanApplicationRepository loanApplicationRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final ProductRequiredDocumentRepository requiredDocumentRepository;
    private final DocumentRepository documentRepository;
    private final DocumentReviewRepository documentReviewRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final AuditService auditService;

    @Value("${loanflow.eligibility.ruleset-version:1.0.0}")
    private String rulesetVersion;

    public EligibilityAssessmentService(EligibilityAssessmentRepository assessmentRepository,
                                        EligibilityReasonCodeRepository reasonCodeRepository,
                                        LoanApplicationRepository loanApplicationRepository,
                                        CustomerProfileRepository customerProfileRepository,
                                        ProductRequiredDocumentRepository requiredDocumentRepository,
                                        DocumentRepository documentRepository,
                                        DocumentReviewRepository documentReviewRepository,
                                        UserRepository userRepository,
                                        ObjectMapper objectMapper,
                                        AuditService auditService) {
        this.assessmentRepository = assessmentRepository;
        this.reasonCodeRepository = reasonCodeRepository;
        this.loanApplicationRepository = loanApplicationRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.requiredDocumentRepository = requiredDocumentRepository;
        this.documentRepository = documentRepository;
        this.documentReviewRepository = documentReviewRepository;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.auditService = auditService;
    }

    public void setRulesetVersion(String rulesetVersion) {
        this.rulesetVersion = rulesetVersion;
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
    public EligibilityAssessmentResponse evaluateEligibility(Long applicationId) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customerProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        if (!ApplicationStatus.SUBMITTED.name().equals(application.getStatus())) {
            throw new ResourceConflictException("Application must be in SUBMITTED status to perform eligibility assessment");
        }

        LoanProduct product = application.getProduct();
        List<ReasonCodeResponse> reasons = new ArrayList<>();

        // Rule A — Application amount
        BigDecimal reqAmount = application.getRequestedAmount();
        if (reqAmount.compareTo(product.getMinAmount()) < 0) {
            reasons.add(new ReasonCodeResponse(CODE_AMOUNT_BELOW_MIN,
                    "Requested loan amount (" + reqAmount + ") is below the product minimum (" + product.getMinAmount() + ")."));
        } else if (reqAmount.compareTo(product.getMaxAmount()) > 0) {
            reasons.add(new ReasonCodeResponse(CODE_AMOUNT_ABOVE_MAX,
                    "Requested loan amount (" + reqAmount + ") exceeds the product maximum (" + product.getMaxAmount() + ")."));
        }

        // Rule B — Loan tenure
        Integer termMonths = application.getTermMonths();
        if (termMonths < product.getMinTermMonths()) {
            reasons.add(new ReasonCodeResponse(CODE_TENURE_BELOW_MIN,
                    "Requested term (" + termMonths + " months) is below the product minimum (" + product.getMinTermMonths() + " months)."));
        } else if (termMonths > product.getMaxTermMonths()) {
            reasons.add(new ReasonCodeResponse(CODE_TENURE_ABOVE_MAX,
                    "Requested term (" + termMonths + " months) exceeds the product maximum (" + product.getMaxTermMonths() + " months)."));
        }

        // Rule C — Required documents
        List<ProductRequiredDocument> requiredDocs = requiredDocumentRepository.findByProductId(product.getId())
                .stream()
                .filter(ProductRequiredDocument::getIsMandatory)
                .collect(Collectors.toList());

        List<Document> uploadedDocs = documentRepository.findByApplicationId(application.getId());
        Map<String, List<Document>> docsByType = uploadedDocs.stream()
                .collect(Collectors.groupingBy(Document::getDocumentType));

        for (ProductRequiredDocument reqDoc : requiredDocs) {
            String docType = reqDoc.getDocumentType();
            List<Document> docs = docsByType.get(docType);

            if (docs == null || docs.isEmpty()) {
                reasons.add(new ReasonCodeResponse(CODE_DOC_MISSING,
                        "Mandatory document of type '" + docType + "' is missing for product '" + product.getName() + "'."));
            } else {
                Document doc = docs.get(0);
                List<DocumentReview> reviews = documentReviewRepository.findByDocumentIdOrderByReviewedAtDesc(doc.getId());
                if (reviews.isEmpty()) {
                    reasons.add(new ReasonCodeResponse(CODE_DOC_PENDING,
                            "Mandatory document of type '" + docType + "' is pending verification."));
                } else {
                    String reviewStatus = reviews.get(0).getStatus();
                    if ("REJECTED".equalsIgnoreCase(reviewStatus)) {
                        reasons.add(new ReasonCodeResponse(CODE_DOC_REJECTED,
                                "Mandatory document of type '" + docType + "' was rejected."));
                    } else if ("PENDING".equalsIgnoreCase(reviewStatus)) {
                        reasons.add(new ReasonCodeResponse(CODE_DOC_PENDING,
                                "Mandatory document of type '" + docType + "' is pending verification."));
                    } else if (!"APPROVED".equalsIgnoreCase(reviewStatus) && !"ACCEPTED".equalsIgnoreCase(reviewStatus)) {
                        reasons.add(new ReasonCodeResponse(CODE_DOC_PENDING,
                                "Mandatory document of type '" + docType + "' is not yet approved."));
                    }
                }
            }
        }

        // Determine decision
        String decision;
        if (reasons.isEmpty()) {
            decision = DECISION_ELIGIBLE;
        } else {
            boolean hasHardFailure = reasons.stream().anyMatch(r ->
                    r.getCode().equals(CODE_AMOUNT_BELOW_MIN) ||
                    r.getCode().equals(CODE_AMOUNT_ABOVE_MAX) ||
                    r.getCode().equals(CODE_TENURE_BELOW_MIN) ||
                    r.getCode().equals(CODE_TENURE_ABOVE_MAX) ||
                    r.getCode().equals(CODE_DOC_REJECTED)
            );
            decision = hasHardFailure ? DECISION_INELIGIBLE : DECISION_INCOMPLETE;
        }

        // Build snapshot
        Map<String, Object> snapshotMap = new LinkedHashMap<>();
        snapshotMap.put("applicationId", application.getId());
        snapshotMap.put("customerId", customerProfile.getId());
        snapshotMap.put("productId", product.getId());
        snapshotMap.put("productName", product.getName());
        snapshotMap.put("requestedAmount", reqAmount);
        snapshotMap.put("productMinAmount", product.getMinAmount());
        snapshotMap.put("productMaxAmount", product.getMaxAmount());
        snapshotMap.put("termMonths", termMonths);
        snapshotMap.put("productMinTermMonths", product.getMinTermMonths());
        snapshotMap.put("productMaxTermMonths", product.getMaxTermMonths());
        snapshotMap.put("applicationStatus", application.getStatus());
        snapshotMap.put("rulesetVersion", rulesetVersion);
        snapshotMap.put("evaluatedAt", LocalDateTime.now().toString());

        String inputSnapshotJson;
        try {
            inputSnapshotJson = objectMapper.writeValueAsString(snapshotMap);
        } catch (JsonProcessingException e) {
            throw new RuntimeException("Failed to serialize eligibility input snapshot", e);
        }

        EligibilityAssessment assessment = new EligibilityAssessment();
        assessment.setApplication(application);
        assessment.setRulesetVersion(rulesetVersion);
        assessment.setDecision(decision);
        assessment.setInputSnapshot(inputSnapshotJson);
        assessment.setAssessedAt(LocalDateTime.now());

        EligibilityAssessment savedAssessment = assessmentRepository.save(assessment);

        List<EligibilityReasonCode> savedReasonEntities = new ArrayList<>();
        for (ReasonCodeResponse reason : reasons) {
            EligibilityReasonCode reasonEntity = new EligibilityReasonCode();
            reasonEntity.setAssessment(savedAssessment);
            reasonEntity.setCode(reason.getCode());
            reasonEntity.setDescription(reason.getDescription());
            savedReasonEntities.add(reasonCodeRepository.save(reasonEntity));
        }

        auditService.recordAudit("ELIGIBILITY_ASSESSMENT_COMPLETED", "EligibilityAssessment", savedAssessment.getId(), null, inputSnapshotJson, "system");

        return mapToResponse(savedAssessment, savedReasonEntities);
    }

    @Transactional(readOnly = true)
    public List<EligibilityAssessmentResponse> getAssessmentHistory(Long applicationId) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        LoanApplication application = loanApplicationRepository.findByIdAndCustomerId(applicationId, customerProfile.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Loan application not found"));

        List<EligibilityAssessment> assessments = assessmentRepository.findByApplicationIdOrderByAssessedAtDesc(application.getId());

        return assessments.stream()
                .map(assessment -> {
                    List<EligibilityReasonCode> reasons = reasonCodeRepository.findByAssessmentId(assessment.getId());
                    return mapToResponse(assessment, reasons);
                })
                .collect(Collectors.toList());
    }

    private EligibilityAssessmentResponse mapToResponse(EligibilityAssessment assessment, List<EligibilityReasonCode> reasonEntities) {
        EligibilityAssessmentResponse response = new EligibilityAssessmentResponse();
        response.setId(assessment.getId());
        response.setApplicationId(assessment.getApplication().getId());
        response.setDecision(assessment.getDecision());
        response.setIsDemo(true);
        response.setDemoDisclaimer(DECISION_ELIGIBLE.equals(assessment.getDecision()) ? DEMO_PASSED_DISCLAIMER : DEMO_GENERAL_DISCLAIMER);
        response.setRulesetVersion(assessment.getRulesetVersion());
        response.setAssessedAt(assessment.getAssessedAt());
        response.setRequestedAmount(assessment.getApplication().getRequestedAmount());
        response.setTermMonths(assessment.getApplication().getTermMonths());
        response.setProductName(assessment.getApplication().getProduct().getName());

        List<ReasonCodeResponse> reasonDtos = reasonEntities.stream()
                .map(r -> new ReasonCodeResponse(r.getCode(), r.getDescription()))
                .collect(Collectors.toList());
        response.setReasons(reasonDtos);

        return response;
    }
}

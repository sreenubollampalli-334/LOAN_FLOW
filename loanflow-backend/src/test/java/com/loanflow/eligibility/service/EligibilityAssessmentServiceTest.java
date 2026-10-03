package com.loanflow.eligibility.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.service.AuditService;
import com.loanflow.document.entity.Document;
import com.loanflow.document.entity.DocumentReview;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.repository.DocumentReviewRepository;
import com.loanflow.eligibility.dto.EligibilityAssessmentResponse;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class EligibilityAssessmentServiceTest {

    @Mock
    private EligibilityAssessmentRepository assessmentRepository;
    @Mock
    private EligibilityReasonCodeRepository reasonCodeRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private ProductRequiredDocumentRepository requiredDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentReviewRepository documentReviewRepository;
    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;
    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private EligibilityAssessmentService service;

    private User user;
    private CustomerProfile customerProfile;
    private LoanProduct product;
    private LoanApplication application;

    @BeforeEach
    public void setUp() {
        user = new User();
        user.setId(1L);
        user.setEmail("customer@example.com");

        customerProfile = new CustomerProfile();
        customerProfile.setId(10L);
        customerProfile.setUser(user);

        product = new LoanProduct();
        product.setId(100L);
        product.setName("Personal Loan");
        product.setMinAmount(new BigDecimal("1000.00"));
        product.setMaxAmount(new BigDecimal("50000.00"));
        product.setMinTermMonths(6);
        product.setMaxTermMonths(60);

        application = new LoanApplication();
        application.setId(1000L);
        application.setCustomer(customerProfile);
        application.setProduct(product);
        application.setRequestedAmount(new BigDecimal("10000.00"));
        application.setTermMonths(24);
        application.setStatus(ApplicationStatus.SUBMITTED.name());

        Authentication auth = mock(Authentication.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        lenient().when(customerProfileRepository.findByUserId(1L)).thenReturn(Optional.of(customerProfile));
        lenient().when(assessmentRepository.save(any(EligibilityAssessment.class))).thenAnswer(i -> {
            EligibilityAssessment ea = i.getArgument(0);
            ea.setId(500L);
            return ea;
        });
        lenient().when(reasonCodeRepository.save(any(EligibilityReasonCode.class))).thenAnswer(i -> {
            EligibilityReasonCode erc = i.getArgument(0);
            erc.setId(System.currentTimeMillis());
            return erc;
        });
    }

    @Test
    public void testSuccessfulEligibilityAssessment() {
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_ELIGIBLE, response.getDecision());
        assertTrue(response.getIsDemo());
        assertEquals(EligibilityAssessmentService.DEMO_PASSED_DISCLAIMER, response.getDemoDisclaimer());
        assertTrue(response.getReasons().isEmpty());
        verify(assessmentRepository).save(any(EligibilityAssessment.class));
    }

    @Test
    public void testAmountBelowMin() {
        application.setRequestedAmount(new BigDecimal("500.00"));
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_AMOUNT_BELOW_MIN, response.getReasons().get(0).getCode());
    }

    @Test
    public void testAmountAboveMax() {
        application.setRequestedAmount(new BigDecimal("100000.00"));
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_AMOUNT_ABOVE_MAX, response.getReasons().get(0).getCode());
    }

    @Test
    public void testTenureBelowMin() {
        application.setTermMonths(3);
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_TENURE_BELOW_MIN, response.getReasons().get(0).getCode());
    }

    @Test
    public void testTenureAboveMax() {
        application.setTermMonths(72);
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_TENURE_ABOVE_MAX, response.getReasons().get(0).getCode());
    }

    @Test
    public void testMissingMandatoryDocument() {
        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setProduct(product);
        prd.setDocumentType("IDENTITY_PROOF");
        prd.setIsMandatory(true);

        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(List.of(prd));
        when(documentRepository.findByApplicationId(1000L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INCOMPLETE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_DOC_MISSING, response.getReasons().get(0).getCode());
    }

    @Test
    public void testPendingDocument() {
        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setProduct(product);
        prd.setDocumentType("IDENTITY_PROOF");
        prd.setIsMandatory(true);

        Document doc = new Document();
        doc.setId(200L);
        doc.setDocumentType("IDENTITY_PROOF");

        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(List.of(prd));
        when(documentRepository.findByApplicationId(1000L)).thenReturn(List.of(doc));
        when(documentReviewRepository.findByDocumentIdOrderByReviewedAtDesc(200L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INCOMPLETE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_DOC_PENDING, response.getReasons().get(0).getCode());
    }

    @Test
    public void testRejectedDocument() {
        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setProduct(product);
        prd.setDocumentType("IDENTITY_PROOF");
        prd.setIsMandatory(true);

        Document doc = new Document();
        doc.setId(200L);
        doc.setDocumentType("IDENTITY_PROOF");

        DocumentReview review = new DocumentReview();
        review.setStatus("REJECTED");

        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(List.of(prd));
        when(documentRepository.findByApplicationId(1000L)).thenReturn(List.of(doc));
        when(documentReviewRepository.findByDocumentIdOrderByReviewedAtDesc(200L)).thenReturn(List.of(review));

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(1, response.getReasons().size());
        assertEquals(EligibilityAssessmentService.CODE_DOC_REJECTED, response.getReasons().get(0).getCode());
    }

    @Test
    public void testMultipleFailedRules() {
        application.setRequestedAmount(new BigDecimal("100000.00")); // amount above max
        application.setTermMonths(3); // tenure below min

        ProductRequiredDocument prd = new ProductRequiredDocument();
        prd.setProduct(product);
        prd.setDocumentType("IDENTITY_PROOF");
        prd.setIsMandatory(true);

        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(List.of(prd));
        when(documentRepository.findByApplicationId(1000L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals(EligibilityAssessmentService.DECISION_INELIGIBLE, response.getDecision());
        assertEquals(3, response.getReasons().size());
        List<String> codes = response.getReasons().stream().map(r -> r.getCode()).toList();
        assertTrue(codes.contains(EligibilityAssessmentService.CODE_AMOUNT_ABOVE_MAX));
        assertTrue(codes.contains(EligibilityAssessmentService.CODE_TENURE_BELOW_MIN));
        assertTrue(codes.contains(EligibilityAssessmentService.CODE_DOC_MISSING));
    }

    @Test
    public void testDeterministicResultsForIdenticalInputs() {
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response1 = service.evaluateEligibility(1000L);
        EligibilityAssessmentResponse response2 = service.evaluateEligibility(1000L);

        assertEquals(response1.getDecision(), response2.getDecision());
        assertEquals(response1.getReasons().size(), response2.getReasons().size());
    }

    @Test
    public void testRulesetVersionHandling() {
        service.setRulesetVersion("2.1.0");
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));
        when(requiredDocumentRepository.findByProductId(100L)).thenReturn(Collections.emptyList());

        EligibilityAssessmentResponse response = service.evaluateEligibility(1000L);

        assertEquals("2.1.0", response.getRulesetVersion());
    }

    @Test
    public void testDraftApplicationThrowsConflict() {
        application.setStatus(ApplicationStatus.DRAFT.name());
        when(loanApplicationRepository.findByIdAndCustomerId(1000L, 10L)).thenReturn(Optional.of(application));

        assertThrows(ResourceConflictException.class, () -> service.evaluateEligibility(1000L));
    }

    @Test
    public void testMissingApplicationThrowsNotFound() {
        when(loanApplicationRepository.findByIdAndCustomerId(9999L, 10L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> service.evaluateEligibility(9999L));
    }
}

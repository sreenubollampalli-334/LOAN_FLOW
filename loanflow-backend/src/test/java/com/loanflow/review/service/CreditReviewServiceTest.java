package com.loanflow.review.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.entity.AuditLog;
import com.loanflow.audit.service.AuditService;
import com.loanflow.document.repository.DocumentRepository;
import com.loanflow.document.repository.DocumentReviewRepository;
import com.loanflow.dto.PaginatedResponse;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.eligibility.repository.EligibilityReasonCodeRepository;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.outbox.entity.OutboxEvent;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
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
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
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
public class CreditReviewServiceTest {

    @Mock
    private CreditReviewRepository creditReviewRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private EligibilityAssessmentRepository eligibilityAssessmentRepository;
    @Mock
    private EligibilityReasonCodeRepository eligibilityReasonCodeRepository;
    @Mock
    private ProductRequiredDocumentRepository requiredDocumentRepository;
    @Mock
    private DocumentRepository documentRepository;
    @Mock
    private DocumentReviewRepository documentReviewRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private CustomerProfileRepository customerProfileRepository;
    @Mock
    private AuditService auditService;
    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private CreditReviewService service;

    private User officerUser;
    private Role officerRole;
    private User customerUser;
    private CustomerProfile customerProfile;
    private LoanProduct product;
    private LoanApplication application;
    private EligibilityAssessment assessment;

    @BeforeEach
    public void setUp() {
        officerRole = new Role();
        officerRole.setId(2);
        officerRole.setName("CREDIT_OFFICER");

        officerUser = new User();
        officerUser.setId(10L);
        officerUser.setEmail("officer@example.com");
        officerUser.setRoles(Set.of(officerRole));

        customerUser = new User();
        customerUser.setId(20L);
        customerUser.setEmail("customer@example.com");

        customerProfile = new CustomerProfile();
        customerProfile.setId(200L);
        customerProfile.setFirstName("John");
        customerProfile.setLastName("Doe");
        customerProfile.setUser(customerUser);

        product = new LoanProduct();
        product.setId(50L);
        product.setName("Personal Loan");
        product.setMinAmount(new BigDecimal("1000"));
        product.setMaxAmount(new BigDecimal("50000"));

        application = new LoanApplication();
        application.setId(100L);
        application.setCustomer(customerProfile);
        application.setProduct(product);
        application.setRequestedAmount(new BigDecimal("10000"));
        application.setTermMonths(24);
        application.setPurpose("Education");
        application.setStatus(ApplicationStatus.SUBMITTED.name());

        assessment = new EligibilityAssessment();
        assessment.setId(500L);
        assessment.setApplication(application);
        assessment.setDecision("ELIGIBLE");
        assessment.setRulesetVersion("1.0.0");
        assessment.setAssessedAt(LocalDateTime.now());

        Authentication auth = mock(Authentication.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(officerUser);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(officerUser));
    }

    @Test
    public void testGetQueue_Success() {
        when(loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name()))
                .thenReturn(List.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(Collections.emptyList());

        PaginatedResponse<CreditReviewQueueItemResponse> response = service.getQueue(0, 10);

        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals(100L, response.getContent().get(0).getApplicationId());
        assertEquals("ELIGIBLE", response.getContent().get(0).getEligibilityDecision());
        assertFalse(response.getContent().get(0).getHasExistingReview());
    }

    @Test
    public void testGetQueue_ExcludesAlreadyReviewed() {
        CreditReview existingReview = new CreditReview();
        existingReview.setId(1L);

        when(loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name()))
                .thenReturn(List.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(List.of(existingReview));

        PaginatedResponse<CreditReviewQueueItemResponse> response = service.getQueue(0, 10);

        assertEquals(0, response.getTotalElements());
        assertTrue(response.getContent().isEmpty());
    }

    @Test
    public void testGetQueue_ExcludesUnassessed() {
        when(loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name()))
                .thenReturn(List.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(Collections.emptyList());

        PaginatedResponse<CreditReviewQueueItemResponse> response = service.getQueue(0, 10);

        assertEquals(0, response.getTotalElements());
    }

    @Test
    public void testGetReviewDetails_Success() {
        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(eligibilityReasonCodeRepository.findByAssessmentId(500L))
                .thenReturn(Collections.emptyList());
        when(requiredDocumentRepository.findByProductId(50L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByApplicationId(100L)).thenReturn(Collections.emptyList());
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(Collections.emptyList());

        CreditReviewDetailsResponse details = service.getReviewDetails(100L);

        assertNotNull(details);
        assertEquals(100L, details.getApplication().getId());
        assertEquals("John", details.getCustomer().getFirstName());
        assertEquals("Personal Loan", details.getProduct().getName());
        assertEquals("ELIGIBLE", details.getEligibilityAssessment().getDecision());
        assertNull(details.getExistingReview());
    }

    @Test
    public void testCreateReview_Recommend_Success() {
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Solid credit history and employment verification.");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(Collections.emptyList());
        when(creditReviewRepository.save(any(CreditReview.class))).thenAnswer(i -> {
            CreditReview cr = i.getArgument(0);
            cr.setId(99L);
            return cr;
        });

        CreditReviewResponse response = service.createReview(100L, req, "127.0.0.1");

        assertNotNull(response);
        assertEquals(99L, response.getId());
        assertEquals("RECOMMEND", response.getDecision());
        assertEquals("officer@example.com", response.getCreditOfficerEmail());

        verify(auditService).recordAudit(eq("CREDIT_REVIEW_SUBMITTED"), eq("LoanApplication"), eq(100L), isNull(), anyString(), eq("127.0.0.1"));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    public void testCreateReview_DraftApplication_ThrowsConflict() {
        application.setStatus(ApplicationStatus.DRAFT.name());
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Draft review comment");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));

        assertThrows(ResourceConflictException.class, () -> service.createReview(100L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_MissingEligibility_ThrowsConflict() {
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "No eligibility assessment comment");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(Collections.emptyList());

        assertThrows(ResourceConflictException.class, () -> service.createReview(100L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_DuplicateReview_ThrowsConflict() {
        CreditReview existing = new CreditReview();
        existing.setId(1L);

        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Second review comment");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(List.of(existing));

        assertThrows(ResourceConflictException.class, () -> service.createReview(100L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_RecommendIneligible_ThrowsConflict() {
        assessment.setDecision("INELIGIBLE");
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Trying to override ineligible check");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));

        assertThrows(ResourceConflictException.class, () -> service.createReview(100L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_RecommendIncomplete_ThrowsConflict() {
        assessment.setDecision("INCOMPLETE");
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Trying to override incomplete check");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));

        assertThrows(ResourceConflictException.class, () -> service.createReview(100L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_DoNotRecommendIneligible_Allowed() {
        assessment.setDecision("INELIGIBLE");
        CreateCreditReviewRequest req = new CreateCreditReviewRequest("DO_NOT_RECOMMEND", "Fails eligibility rules on amount.");

        when(loanApplicationRepository.findById(100L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(100L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(100L))
                .thenReturn(Collections.emptyList());
        when(creditReviewRepository.save(any(CreditReview.class))).thenAnswer(i -> {
            CreditReview cr = i.getArgument(0);
            cr.setId(101L);
            return cr;
        });

        CreditReviewResponse response = service.createReview(100L, req, "127.0.0.1");

        assertNotNull(response);
        assertEquals("DO_NOT_RECOMMEND", response.getDecision());
    }

    @Test
    public void testNonCreditOfficerRole_ThrowsAccessDenied() {
        User customerUserOnly = new User();
        customerUserOnly.setId(30L);
        customerUserOnly.setEmail("custonly@example.com");

        UserDetailsImpl customerDetails = new UserDetailsImpl(customerUserOnly);
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(customerDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        when(userRepository.findById(30L)).thenReturn(Optional.of(customerUserOnly));

        assertThrows(AccessDeniedException.class, () -> service.getQueue(0, 10));
    }
}

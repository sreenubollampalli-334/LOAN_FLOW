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
import com.loanflow.product.repository.ProductRequiredDocumentRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.review.dto.CreateManagerReviewRequest;
import com.loanflow.review.dto.ManagerReviewDetailsResponse;
import com.loanflow.review.dto.ManagerReviewQueueItemResponse;
import com.loanflow.review.dto.ManagerReviewResponse;
import com.loanflow.review.entity.CreditReview;
import com.loanflow.review.entity.ManagerReview;
import com.loanflow.review.repository.CreditReviewRepository;
import com.loanflow.review.repository.ManagerReviewRepository;
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
public class ManagerReviewServiceTest {

    @Mock
    private ManagerReviewRepository managerReviewRepository;
    @Mock
    private CreditReviewRepository creditReviewRepository;
    @Mock
    private LoanApplicationRepository loanApplicationRepository;
    @Mock
    private LoanRepository loanRepository;
    @Mock
    private ApplicationStatusHistoryRepository statusHistoryRepository;
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
    private ManagerReviewService service;

    private User managerUser;
    private Role managerRole;
    private User customerUser;
    private CustomerProfile customerProfile;
    private LoanProduct product;
    private LoanApplication application;
    private EligibilityAssessment assessment;
    private CreditReview creditReview;

    @BeforeEach
    public void setUp() {
        managerRole = new Role();
        managerRole.setId(3);
        managerRole.setName("MANAGER");

        managerUser = new User();
        managerUser.setId(15L);
        managerUser.setEmail("manager@example.com");
        managerUser.setRoles(Set.of(managerRole));

        customerUser = new User();
        customerUser.setId(25L);
        customerUser.setEmail("customer@example.com");

        customerProfile = new CustomerProfile();
        customerProfile.setId(250L);
        customerProfile.setFirstName("Jane");
        customerProfile.setLastName("Smith");
        customerProfile.setUser(customerUser);

        product = new LoanProduct();
        product.setId(60L);
        product.setName("Home Improvement Loan");
        product.setMinAmount(new BigDecimal("2000"));
        product.setMaxAmount(new BigDecimal("100000"));
        product.setInterestRate(new BigDecimal("0.08"));

        application = new LoanApplication();
        application.setId(200L);
        application.setCustomer(customerProfile);
        application.setProduct(product);
        application.setRequestedAmount(new BigDecimal("20000"));
        application.setTermMonths(36);
        application.setPurpose("Kitchen Remodel");
        application.setStatus(ApplicationStatus.SUBMITTED.name());

        assessment = new EligibilityAssessment();
        assessment.setId(600L);
        assessment.setApplication(application);
        assessment.setDecision("ELIGIBLE");
        assessment.setRulesetVersion("1.0.0");
        assessment.setAssessedAt(LocalDateTime.now());

        creditReview = new CreditReview();
        creditReview.setId(88L);
        creditReview.setApplication(application);
        creditReview.setCreditOfficer(managerUser); // sample user
        creditReview.setDecision("RECOMMEND");
        creditReview.setComments("Strong applicant profile.");
        creditReview.setReviewedAt(LocalDateTime.now());

        Authentication auth = mock(Authentication.class);
        UserDetailsImpl userDetails = new UserDetailsImpl(managerUser);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findById(15L)).thenReturn(Optional.of(managerUser));
    }

    @Test
    public void testGetQueue_Success() {
        when(loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name()))
                .thenReturn(List.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());

        PaginatedResponse<ManagerReviewQueueItemResponse> response = service.getQueue(0, 10);

        assertEquals(1, response.getTotalElements());
        assertEquals(1, response.getContent().size());
        assertEquals(200L, response.getContent().get(0).getApplicationId());
        assertEquals("RECOMMEND", response.getContent().get(0).getCreditOfficerRecommendation());
        assertFalse(response.getContent().get(0).getHasExistingManagerReview());
    }

    @Test
    public void testGetQueue_ExcludesMissingCreditReview() {
        when(loanApplicationRepository.findByStatus(ApplicationStatus.SUBMITTED.name()))
                .thenReturn(List.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());

        PaginatedResponse<ManagerReviewQueueItemResponse> response = service.getQueue(0, 10);

        assertEquals(0, response.getTotalElements());
    }

    @Test
    public void testGetReviewDetails_Success() {
        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));
        when(eligibilityReasonCodeRepository.findByAssessmentId(600L))
                .thenReturn(Collections.emptyList());
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(requiredDocumentRepository.findByProductId(60L)).thenReturn(Collections.emptyList());
        when(documentRepository.findByApplicationId(200L)).thenReturn(Collections.emptyList());
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());

        ManagerReviewDetailsResponse details = service.getReviewDetails(200L);

        assertNotNull(details);
        assertEquals(200L, details.getApplication().getId());
        assertEquals("RECOMMEND", details.getCreditOfficerReview().getDecision());
        assertNull(details.getExistingManagerReview());
    }

    @Test
    public void testCreateReview_Approve_Success_CreatesLoan() {
        CreateManagerReviewRequest req = new CreateManagerReviewRequest("APPROVE", "Approved loan application and terms.");

        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));
        when(managerReviewRepository.save(any(ManagerReview.class))).thenAnswer(i -> {
            ManagerReview mr = i.getArgument(0);
            mr.setId(77L);
            return mr;
        });
        when(loanRepository.findByApplicationId(200L)).thenReturn(Optional.empty());
        when(loanRepository.save(any(Loan.class))).thenAnswer(i -> {
            Loan l = i.getArgument(0);
            l.setId(505L);
            return l;
        });

        ManagerReviewResponse response = service.createReview(200L, req, "127.0.0.1");

        assertNotNull(response);
        assertEquals(77L, response.getId());
        assertEquals("APPROVE", response.getDecision());
        assertEquals(ApplicationStatus.APPROVED.name(), response.getApplicationStatus());
        assertEquals(505L, response.getLoanId());

        verify(loanApplicationRepository).save(application);
        verify(statusHistoryRepository).save(any(ApplicationStatusHistory.class));
        verify(loanRepository).save(any(Loan.class));
        verify(auditService).recordAudit(eq("MANAGER_REVIEW_SUBMITTED"), eq("LoanApplication"), eq(200L), isNull(), anyString(), eq("127.0.0.1"));
        verify(outboxEventRepository).save(any(OutboxEvent.class));
    }

    @Test
    public void testCreateReview_Reject_Success() {
        CreateManagerReviewRequest req = new CreateManagerReviewRequest("REJECT", "Manager decision to reject based on policy.");

        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));
        when(managerReviewRepository.save(any(ManagerReview.class))).thenAnswer(i -> {
            ManagerReview mr = i.getArgument(0);
            mr.setId(78L);
            return mr;
        });

        ManagerReviewResponse response = service.createReview(200L, req, "127.0.0.1");

        assertNotNull(response);
        assertEquals("REJECT", response.getDecision());
        assertEquals(ApplicationStatus.REJECTED.name(), response.getApplicationStatus());
        assertNull(response.getLoanId());

        verify(loanRepository, never()).save(any(Loan.class));
    }

    @Test
    public void testCreateReview_ApproveIneligible_ThrowsConflict() {
        assessment.setDecision("INELIGIBLE");
        CreateManagerReviewRequest req = new CreateManagerReviewRequest("APPROVE", "Trying to approve ineligible application");

        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());
        when(eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(200L))
                .thenReturn(List.of(assessment));

        assertThrows(ResourceConflictException.class, () -> service.createReview(200L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_MissingCreditReview_ThrowsConflict() {
        CreateManagerReviewRequest req = new CreateManagerReviewRequest("APPROVE", "No credit review comment");

        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(Collections.emptyList());

        assertThrows(ResourceConflictException.class, () -> service.createReview(200L, req, "127.0.0.1"));
    }

    @Test
    public void testCreateReview_DuplicateManagerReview_ThrowsConflict() {
        ManagerReview existing = new ManagerReview();
        existing.setId(1L);
        CreateManagerReviewRequest req = new CreateManagerReviewRequest("APPROVE", "Second manager review comment");

        when(loanApplicationRepository.findById(200L)).thenReturn(Optional.of(application));
        when(creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(creditReview));
        when(managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(200L))
                .thenReturn(List.of(existing));

        assertThrows(ResourceConflictException.class, () -> service.createReview(200L, req, "127.0.0.1"));
    }

    @Test
    public void testNonManagerRole_ThrowsAccessDenied() {
        User officerUserOnly = new User();
        officerUserOnly.setId(35L);
        officerUserOnly.setEmail("officeronly@example.com");

        UserDetailsImpl userDetails = new UserDetailsImpl(officerUserOnly);
        Authentication auth = mock(Authentication.class);
        lenient().when(auth.getPrincipal()).thenReturn(userDetails);

        SecurityContext securityContext = mock(SecurityContext.class);
        lenient().when(securityContext.getAuthentication()).thenReturn(auth);
        SecurityContextHolder.setContext(securityContext);

        lenient().when(userRepository.findById(35L)).thenReturn(Optional.of(officerUserOnly));

        assertThrows(AccessDeniedException.class, () -> service.getQueue(0, 10));
    }
}

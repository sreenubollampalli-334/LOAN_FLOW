package com.loanflow.disbursement.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.audit.entity.AuditLog;
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
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DisbursementServiceTest {

    @Mock
    private DisbursementRepository disbursementRepository;

    @Mock
    private DisbursementAttemptRepository attemptRepository;

    @Mock
    private DisbursementIdempotencyKeyRepository idempotencyKeyRepository;

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private LoanApplicationRepository loanApplicationRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AuditService auditService;

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private com.loanflow.repayment.service.RepaymentScheduleService repaymentScheduleService;

    @Spy
    private DisbursementProcessor disbursementProcessor = new DisbursementProcessor();

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private DisbursementService disbursementService;

    private User adminUser;
    private User customerUser;
    private CustomerProfile customerProfile;
    private Loan sampleLoan;
    private LoanApplication sampleApp;

    @BeforeEach
    void setUp() {
        DisbursementProcessor.clearTestFailure();

        Role adminRole = new Role();
        adminRole.setId(1);
        adminRole.setName("ADMIN");

        adminUser = new User();
        adminUser.setId(10L);
        adminUser.setEmail("admin@loanflow.com");
        adminUser.setRoles(Set.of(adminRole));

        Role customerRole = new Role();
        customerRole.setId(2);
        customerRole.setName("CUSTOMER");

        customerUser = new User();
        customerUser.setId(20L);
        customerUser.setEmail("customer@loanflow.com");
        customerUser.setRoles(Set.of(customerRole));

        customerProfile = new CustomerProfile();
        customerProfile.setId(100L);
        customerProfile.setUser(customerUser);
        customerProfile.setFirstName("John");
        customerProfile.setLastName("Doe");

        sampleApp = new LoanApplication();
        sampleApp.setId(50L);
        sampleApp.setCustomer(customerProfile);

        com.loanflow.product.entity.LoanProduct product = new com.loanflow.product.entity.LoanProduct();
        product.setId(1L);
        product.setName("Personal Quick Loan");

        sampleLoan = new Loan();
        sampleLoan.setId(200L);
        sampleLoan.setApplication(sampleApp);
        sampleLoan.setCustomer(customerProfile);
        sampleLoan.setProduct(product);
        sampleLoan.setPrincipalAmount(new BigDecimal("15000.00"));
        sampleLoan.setTermMonths(24);
        sampleLoan.setStatus("APPROVED");
        sampleLoan.setCreatedAt(LocalDateTime.now());
    }

    @AfterEach
    void tearDown() {
        DisbursementProcessor.clearTestFailure();
    }

    private void authenticateUser(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    private String calculateRequestHash(Long loanId, InitiateDisbursementRequest request) {
        String data = "LOAN_DISBURSEMENT:" + loanId;
        try {
            java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(data.hashCode());
        }
    }

    @Test
    @DisplayName("getQueue: Admin retrieves paginated queue of approved loans successfully")
    void getQueue_adminSuccess() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByStatus("APPROVED")).thenReturn(List.of(sampleLoan));
        when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.empty());

        PaginatedResponse<DisbursementQueueItemResponse> response = disbursementService.getQueue(0, 10);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(200L, response.getContent().get(0).getLoanId());
        assertEquals("John Doe", response.getContent().get(0).getCustomerName());
        assertEquals("PENDING_REQUEST", response.getContent().get(0).getDisbursementStatus());
    }

    @Test
    @DisplayName("getQueue: Non-Admin user throws AccessDeniedException")
    void getQueue_nonAdminThrowsAccessDenied() {
        authenticateUser(customerUser);
        when(userRepository.findById(20L)).thenReturn(Optional.of(customerUser));

        assertThrows(AccessDeniedException.class, () -> disbursementService.getQueue(0, 10));
    }

    @Test
    @DisplayName("initiateDisbursement: Successful simulated disbursement updates loan status to DISBURSED")
    void initiateDisbursement_success() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sampleLoan));
        when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.empty());

        Disbursement pendingDisb = new Disbursement();
        pendingDisb.setId(300L);
        pendingDisb.setLoan(sampleLoan);
        pendingDisb.setAmount(new BigDecimal("15000.00"));
        pendingDisb.setStatus("PENDING");

        when(disbursementRepository.save(any(Disbursement.class))).thenAnswer(i -> {
            Disbursement d = i.getArgument(0);
            if (d.getId() == null) d.setId(300L);
            return d;
        });

        when(attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(300L)).thenReturn(Collections.emptyList());

        when(attemptRepository.save(any(DisbursementAttempt.class))).thenAnswer(i -> {
            DisbursementAttempt da = i.getArgument(0);
            da.setId(400L);
            return da;
        });

        DisbursementResponse response = disbursementService.initiateDisbursement(200L, null, null, "127.0.0.1");

        assertNotNull(response);
        assertEquals("DISBURSED", response.getStatus());
        assertTrue(response.getIsDemo());
        assertEquals("DISBURSED", sampleLoan.getStatus());
        verify(repaymentScheduleService, times(1)).generateAndSaveSchedule(sampleLoan);
        verify(auditService, times(1)).recordAudit(anyString(), eq("Loan"), eq(200L), isNull(), anyString(), eq("127.0.0.1"));
        verify(outboxEventRepository, times(1)).save(any(OutboxEvent.class));
    }

    @Test
    @DisplayName("initiateDisbursement: Test-only simulated failure records failed attempt without generating schedule")
    void initiateDisbursement_simulatedFailure() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sampleLoan));

        Disbursement pendingDisb = new Disbursement();
        pendingDisb.setId(300L);
        pendingDisb.setLoan(sampleLoan);
        pendingDisb.setAmount(new BigDecimal("15000.00"));
        pendingDisb.setStatus("PENDING");

        when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.of(pendingDisb));
        when(attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(300L)).thenReturn(Collections.emptyList());
        when(disbursementRepository.save(any(Disbursement.class))).thenAnswer(i -> i.getArgument(0));
        when(attemptRepository.save(any(DisbursementAttempt.class))).thenAnswer(i -> {
            DisbursementAttempt da = i.getArgument(0);
            da.setId(401L);
            return da;
        });

        // Trigger test-only failure mechanism
        DisbursementProcessor.setTestFailure("Bank network timeout simulated");

        DisbursementResponse response = disbursementService.initiateDisbursement(200L, null, null, "127.0.0.1");

        assertNotNull(response);
        assertEquals("FAILED", response.getStatus());
        assertEquals("APPROVED", sampleLoan.getStatus()); // Loan remains APPROVED for retry
        verify(repaymentScheduleService, never()).generateAndSaveSchedule(any(Loan.class));
    }

    @Test
    @DisplayName("initiateDisbursement: Schedule generation exception propagates to trigger transaction rollback")
    void initiateDisbursement_scheduleGenerationFailureThrowsException() {
        authenticateUser(adminUser);
        lenient().when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        lenient().when(loanRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sampleLoan));
        lenient().when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.empty());

        Disbursement pendingDisb = new Disbursement();
        pendingDisb.setId(300L);
        pendingDisb.setLoan(sampleLoan);
        pendingDisb.setAmount(new BigDecimal("15000.00"));
        pendingDisb.setStatus("PENDING");

        lenient().when(disbursementRepository.save(any(Disbursement.class))).thenAnswer(i -> {
            Disbursement d = i.getArgument(0);
            if (d.getId() == null) d.setId(300L);
            return d;
        });

        lenient().when(attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(300L)).thenReturn(Collections.emptyList());
        lenient().when(attemptRepository.save(any(DisbursementAttempt.class))).thenAnswer(i -> {
            DisbursementAttempt da = i.getArgument(0);
            da.setId(400L);
            return da;
        });

        doThrow(new RuntimeException("Schedule persistence constraint violation"))
                .when(repaymentScheduleService).generateAndSaveSchedule(sampleLoan);

        assertThrows(RuntimeException.class, () ->
                disbursementService.initiateDisbursement(200L, null, null, "127.0.0.1"));
    }

    @Test
    @DisplayName("initiateDisbursement: Unapproved loan throws ResourceConflictException")
    void initiateDisbursement_unapprovedLoanThrowsConflict() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        sampleLoan.setStatus("SUBMITTED");
        when(loanRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sampleLoan));

        assertThrows(ResourceConflictException.class, () ->
                disbursementService.initiateDisbursement(200L, null, null, "127.0.0.1"));
    }

    @Test
    @DisplayName("initiateDisbursement: Exceeding max 3 retry attempts throws ResourceConflictException")
    void initiateDisbursement_maxRetryAttemptsExceeded() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));
        when(loanRepository.findByIdForUpdate(200L)).thenReturn(Optional.of(sampleLoan));

        Disbursement disb = new Disbursement();
        disb.setId(300L);
        disb.setLoan(sampleLoan);
        disb.setStatus("FAILED");

        DisbursementAttempt a1 = new DisbursementAttempt();
        a1.setAttemptNumber(1);
        DisbursementAttempt a2 = new DisbursementAttempt();
        a2.setAttemptNumber(2);
        DisbursementAttempt a3 = new DisbursementAttempt();
        a3.setAttemptNumber(3);

        when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.of(disb));
        when(attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(300L)).thenReturn(List.of(a1, a2, a3));

        ResourceConflictException ex = assertThrows(ResourceConflictException.class, () ->
                disbursementService.initiateDisbursement(200L, null, null, "127.0.0.1"));
        assertTrue(ex.getMessage().contains("Maximum disbursement retry limit"));
    }

    @Test
    @DisplayName("initiateDisbursement: Database-backed idempotency returns persisted response on repeated identical key")
    void initiateDisbursement_idempotencyKeyReused() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));

        String key = "IDEM-KEY-999";
        DisbursementIdempotencyKey dik = new DisbursementIdempotencyKey();
        dik.setIdempotencyKey(key);
        dik.setRequestHash(calculateRequestHash(200L, null));
        dik.setResponsePayload("{\"id\":300,\"loanId\":200,\"amount\":15000.00,\"status\":\"DISBURSED\",\"isDemo\":true,\"demoDisclaimer\":\"Simulated demo loan disbursement. No real funds were transferred.\",\"attemptsCount\":1}");

        when(idempotencyKeyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(dik));

        DisbursementResponse response = disbursementService.initiateDisbursement(200L, null, key, "127.0.0.1");

        assertNotNull(response);
        assertEquals(300L, response.getId());
        assertEquals("DISBURSED", response.getStatus());
        verify(loanRepository, never()).findByIdForUpdate(anyLong()); // Returned directly from DB idempotency record
    }

    @Test
    @DisplayName("initiateDisbursement: Reusing idempotency key with different request payload throws ResourceConflictException")
    void initiateDisbursement_idempotencyKeyConflictDifferentPayload() {
        authenticateUser(adminUser);
        when(userRepository.findById(10L)).thenReturn(Optional.of(adminUser));

        String key = "IDEM-KEY-777";
        DisbursementIdempotencyKey dik = new DisbursementIdempotencyKey();
        dik.setIdempotencyKey(key);
        dik.setRequestHash("LOAN_DISBURSEMENT:999"); // Different loan / request hash

        when(idempotencyKeyRepository.findByIdempotencyKey(key)).thenReturn(Optional.of(dik));

        assertThrows(ResourceConflictException.class, () ->
                disbursementService.initiateDisbursement(200L, null, key, "127.0.0.1"));
    }

    @Test
    @DisplayName("getCustomerLoanDisbursement: Customer retrieves own loan disbursement status")
    void getCustomerLoanDisbursement_success() {
        authenticateUser(customerUser);
        when(userRepository.findById(20L)).thenReturn(Optional.of(customerUser));
        when(customerProfileRepository.findByUserId(20L)).thenReturn(Optional.of(customerProfile));
        when(loanApplicationRepository.findByIdAndCustomerId(50L, 100L)).thenReturn(Optional.of(sampleApp));
        when(loanRepository.findByApplicationId(50L)).thenReturn(Optional.of(sampleLoan));

        Disbursement disb = new Disbursement();
        disb.setId(300L);
        disb.setLoan(sampleLoan);
        disb.setAmount(new BigDecimal("15000.00"));
        disb.setStatus("DISBURSED");

        when(disbursementRepository.findByLoanId(200L)).thenReturn(Optional.of(disb));
        when(attemptRepository.findByDisbursementIdOrderByAttemptNumberAsc(300L)).thenReturn(Collections.emptyList());

        DisbursementResponse response = disbursementService.getCustomerLoanDisbursement(50L);

        assertNotNull(response);
        assertEquals(300L, response.getId());
        assertEquals("DISBURSED", response.getStatus());
        assertTrue(response.getIsDemo());
    }
}

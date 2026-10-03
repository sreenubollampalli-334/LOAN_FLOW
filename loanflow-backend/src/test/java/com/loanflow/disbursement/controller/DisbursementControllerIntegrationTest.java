package com.loanflow.disbursement.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.audit.repository.AuditLogRepository;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.disbursement.dto.InitiateDisbursementRequest;
import com.loanflow.disbursement.entity.DisbursementIdempotencyKey;
import com.loanflow.disbursement.repository.DisbursementAttemptRepository;
import com.loanflow.disbursement.repository.DisbursementIdempotencyKeyRepository;
import com.loanflow.disbursement.repository.DisbursementRepository;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Optional;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:mysql://localhost:3307/loanflow",
    "spring.datasource.username=loanflow_user",
    "spring.datasource.password=loanflow_pass",
    "spring.flyway.enabled=false",
    "spring.jpa.hibernate.ddl-auto=validate",
    "spring.data.redis.host=localhost",
    "spring.data.redis.port=6379",
    "loanflow.security.jwt.secret=MTIzNDU2Nzg5MDEyMzQ1Njc4OTAxMjM0NTY3ODkwMTI=",
    "loanflow.storage.s3.endpoint=http://localhost:9000",
    "loanflow.storage.s3.region=us-east-1",
    "loanflow.storage.s3.bucket=test",
    "loanflow.storage.s3.access-key=test",
    "loanflow.storage.s3.secret-key=test"
})
@AutoConfigureMockMvc
@Transactional
public class DisbursementControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private LoanProductRepository loanProductRepository;

    @Autowired
    private LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private DisbursementRepository disbursementRepository;

    @Autowired
    private DisbursementAttemptRepository attemptRepository;

    @Autowired
    private DisbursementIdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    @Autowired
    private com.loanflow.repayment.repository.RepaymentScheduleRepository repaymentScheduleRepository;

    private ObjectMapper objectMapper;

    private String adminToken;
    private String customerToken;
    private String officerToken;
    private String managerToken;

    private CustomerProfile customerProfile;
    private Loan approvedLoan;
    private LoanApplication approvedApp;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ensureRole("ADMIN");
        ensureRole("CUSTOMER");
        ensureRole("CREDIT_OFFICER");
        ensureRole("MANAGER");

        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // Tokens
        customerToken = createUserAndGetToken("disb_cust_" + suffix + "@test.com", "CUSTOMER");
        adminToken = createUserAndGetToken("disb_admin_" + suffix + "@test.com", "ADMIN");
        officerToken = createUserAndGetToken("disb_officer_" + suffix + "@test.com", "CREDIT_OFFICER");
        managerToken = createUserAndGetToken("disb_mgr_" + suffix + "@test.com", "MANAGER");

        User custUser = userRepository.findByEmail("disb_cust_" + suffix + "@test.com").orElseThrow();
        customerProfile = customerProfileRepository.findByUserId(custUser.getId()).orElseThrow();

        // Product
        LoanProduct product = loanProductRepository.findByName("Disbursement Integration Product")
                .orElseGet(() -> {
                    LoanProduct p = new LoanProduct();
                    p.setName("Disbursement Integration Product");
                    p.setDescription("Test Product");
                    p.setMinAmount(new BigDecimal("1000.00"));
                    p.setMaxAmount(new BigDecimal("50000.00"));
                    p.setInterestRate(new BigDecimal("0.1000"));
                    p.setMinTermMonths(6);
                    p.setMaxTermMonths(60);
                    p.setIsActive(true);
                    return loanProductRepository.save(p);
                });

        // Application
        approvedApp = new LoanApplication();
        approvedApp.setCustomer(customerProfile);
        approvedApp.setProduct(product);
        approvedApp.setRequestedAmount(new BigDecimal("20000.00"));
        approvedApp.setTermMonths(24);
        approvedApp.setStatus(ApplicationStatus.APPROVED.name());
        approvedApp = loanApplicationRepository.save(approvedApp);

        // Loan
        approvedLoan = new Loan();
        approvedLoan.setApplication(approvedApp);
        approvedLoan.setCustomer(customerProfile);
        approvedLoan.setProduct(product);
        approvedLoan.setPrincipalAmount(new BigDecimal("20000.00"));
        approvedLoan.setTermMonths(24);
        approvedLoan.setInterestRate(new BigDecimal("0.1050"));
        approvedLoan.setInterestCalculationMethod("FLAT");
        approvedLoan.setStatus("APPROVED");
        approvedLoan = loanRepository.save(approvedLoan);
    }

    private void ensureRole(String roleName) {
        if (roleRepository.findByName(roleName).isEmpty()) {
            Role role = new Role();
            role.setName(roleName);
            roleRepository.save(role);
        }
    }

    private String createUserAndGetToken(String email, String roleName) throws Exception {
        RegistrationRequest regRequest = new RegistrationRequest();
        regRequest.setFirstName("Disb");
        regRequest.setLastName("User");
        regRequest.setEmail(email);
        regRequest.setPassword("securePassword123");
        regRequest.setDob(LocalDate.of(1990, 1, 1));
        regRequest.setPhone("9876543210");

        String responseStr = mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(regRequest)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(responseStr, AuthResponse.class);

        if (!roleName.equals("CUSTOMER")) {
            User user = userRepository.findByEmail(email).orElseThrow();
            Role newRole = roleRepository.findByName(roleName).orElseThrow();
            user.getRoles().clear();
            user.getRoles().add(newRole);
            userRepository.save(user);
        }

        return authResponse.getToken();
    }

    @Test
    @DisplayName("GET /api/v1/disbursements/queue - Admin authorized returns queue")
    void getQueue_adminAuthorized_returnsOk() throws Exception {
        mockMvc.perform(get("/api/v1/disbursements/queue")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].loanId").value(approvedLoan.getId()))
                .andExpect(jsonPath("$.content[0].loanStatus").value("APPROVED"));
    }

    @Test
    @DisplayName("GET /api/v1/disbursements/queue - Customer forbidden returns 403")
    void getQueue_customerForbidden_returns403() throws Exception {
        mockMvc.perform(get("/api/v1/disbursements/queue")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/disbursements/queue - Unauthenticated returns 401")
    void getQueue_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/disbursements/queue"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("POST /api/v1/disbursements/loans/{loanId} - Admin initiates simulated disbursement successfully")
    void initiateDisbursement_adminSuccess() throws Exception {
        mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.loanId").value(approvedLoan.getId()))
                .andExpect(jsonPath("$.status").value("DISBURSED"))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoDisclaimer").isNotEmpty());

        Loan updatedLoan = loanRepository.findById(approvedLoan.getId()).orElseThrow();
        assertEquals("DISBURSED", updatedLoan.getStatus());

        var schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(approvedLoan.getId());
        assertEquals(24, schedules.size());
    }

    @Test
    @DisplayName("POST /api/v1/disbursements/loans/{loanId} - Customer & Officer forbidden returns 403")
    void initiateDisbursement_nonAdminForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/disbursements/loans/{loanId} - Test header failure simulation records failed attempt")
    void initiateDisbursement_testSimulatedFailure() throws Exception {
        mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Test-Simulate-Failure", "true")
                        .header("X-Test-Failure-Reason", "Integration bank timeout test")
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("FAILED"));

        Loan updatedLoan = loanRepository.findById(approvedLoan.getId()).orElseThrow();
        assertEquals("APPROVED", updatedLoan.getStatus()); // Remains APPROVED for retry

        var schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(approvedLoan.getId());
        assertTrue(schedules.isEmpty()); // No schedule created on failure
    }

    @Test
    @DisplayName("POST /api/v1/disbursements/loans/{loanId} - Idempotency key persists in DB and returns identical response")
    void initiateDisbursement_idempotentRequest() throws Exception {
        String key = "IDEM-INTEG-" + UUID.randomUUID();

        // First attempt
        MvcResult res1 = mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        // Verify DB persistence of idempotency key
        Optional<DisbursementIdempotencyKey> dikOpt = idempotencyKeyRepository.findByIdempotencyKey(key);
        assertTrue(dikOpt.isPresent());
        assertEquals(approvedLoan.getId(), dikOpt.get().getLoan().getId());

        // Second attempt with same key
        MvcResult res2 = mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .header("X-Idempotency-Key", key)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        assertEquals(res1.getResponse().getContentAsString(), res2.getResponse().getContentAsString());

        var schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(approvedLoan.getId());
        assertEquals(24, schedules.size()); // Exactly 24 schedules, not duplicated
    }

    @Test
    @DisplayName("GET /api/v1/disbursements/{disbursementId} - Admin & Manager can view disbursement details")
    void getDisbursementById_adminAndManagerSuccess() throws Exception {
        MvcResult res = mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andReturn();

        Long disbId = objectMapper.readTree(res.getResponse().getContentAsString()).get("id").asLong();

        // Admin access
        mockMvc.perform(get("/api/v1/disbursements/" + disbId)
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disbId))
                .andExpect(jsonPath("$.status").value("DISBURSED"));

        // Manager access
        mockMvc.perform(get("/api/v1/disbursements/" + disbId)
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(disbId));
    }

    @Test
    @DisplayName("GET /api/v1/loan-applications/{applicationId}/disbursement - Customer owner can view own disbursement")
    void getCustomerLoanDisbursement_customerOwnerSuccess() throws Exception {
        // Disburse loan first
        mockMvc.perform(post("/api/v1/disbursements/loans/" + approvedLoan.getId())
                        .header("Authorization", "Bearer " + adminToken)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated());

        // Customer views own application's disbursement
        mockMvc.perform(get("/api/v1/loan-applications/" + approvedApp.getId() + "/disbursement")
                        .header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISBURSED"))
                .andExpect(jsonPath("$.isDemo").value(true));
    }
}

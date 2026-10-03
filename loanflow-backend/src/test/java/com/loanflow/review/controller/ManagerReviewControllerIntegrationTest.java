package com.loanflow.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.audit.repository.AuditLogRepository;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
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
import com.loanflow.review.dto.CreateManagerReviewRequest;
import com.loanflow.review.entity.CreditReview;
import com.loanflow.review.entity.ManagerReview;
import com.loanflow.review.repository.CreditReviewRepository;
import com.loanflow.review.repository.ManagerReviewRepository;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.RoleRepository;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

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
public class ManagerReviewControllerIntegrationTest {

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
    private EligibilityAssessmentRepository eligibilityAssessmentRepository;

    @Autowired
    private CreditReviewRepository creditReviewRepository;

    @Autowired
    private ManagerReviewRepository managerReviewRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private OutboxEventRepository outboxEventRepository;

    private ObjectMapper objectMapper;
    private LoanProduct testProduct;

    @BeforeEach
    public void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        if (roleRepository.findByName("CUSTOMER").isEmpty()) {
            Role role = new Role();
            role.setName("CUSTOMER");
            roleRepository.save(role);
        }
        if (roleRepository.findByName("CREDIT_OFFICER").isEmpty()) {
            Role role = new Role();
            role.setName("CREDIT_OFFICER");
            roleRepository.save(role);
        }
        if (roleRepository.findByName("MANAGER").isEmpty()) {
            Role role = new Role();
            role.setName("MANAGER");
            roleRepository.save(role);
        }

        testProduct = loanProductRepository.findByName("Manager Review Product Test")
                .orElseGet(() -> {
                    LoanProduct product = new LoanProduct();
                    product.setName("Manager Review Product Test");
                    product.setDescription("Test Product");
                    product.setMinAmount(new BigDecimal("1000.00"));
                    product.setMaxAmount(new BigDecimal("50000.00"));
                    product.setInterestRate(new BigDecimal("0.1000"));
                    product.setMinTermMonths(6);
                    product.setMaxTermMonths(60);
                    product.setIsActive(true);
                    return loanProductRepository.save(product);
                });
    }

    private String createUserAndGetToken(String email, String roleName) throws Exception {
        RegistrationRequest regRequest = new RegistrationRequest();
        regRequest.setFirstName("MgrReview");
        regRequest.setLastName("User");
        regRequest.setEmail(email);
        regRequest.setPassword("securePassword123");
        regRequest.setDob(LocalDate.of(1990, 1, 1));
        regRequest.setPhone("9876543210");

        String responseStr = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(responseStr, AuthResponse.class);

        if (!roleName.equals("CUSTOMER")) {
            User user = userRepository.findByEmail(email).get();
            Role newRole = roleRepository.findByName(roleName).get();
            user.getRoles().clear();
            user.getRoles().add(newRole);
            userRepository.save(user);
        }

        return authResponse.getToken();
    }

    private LoanApplication createReadyForManagerApp(String userEmail, String officerEmail, String eligDecision, String officerDecision) {
        User custUser = userRepository.findByEmail(userEmail).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(custUser.getId()).get();

        LoanApplication app = new LoanApplication();
        app.setCustomer(profile);
        app.setProduct(testProduct);
        app.setRequestedAmount(new BigDecimal("15000.00"));
        app.setTermMonths(36);
        app.setPurpose("Expansion");
        app.setStatus(ApplicationStatus.SUBMITTED.name());

        LoanApplication savedApp = loanApplicationRepository.save(app);

        EligibilityAssessment assessment = new EligibilityAssessment();
        assessment.setApplication(savedApp);
        assessment.setDecision(eligDecision);
        assessment.setRulesetVersion("1.0.0");
        assessment.setInputSnapshot("{}");
        assessment.setAssessedAt(LocalDateTime.now());
        eligibilityAssessmentRepository.save(assessment);

        User officerUser = userRepository.findByEmail(officerEmail).get();
        CreditReview creditReview = new CreditReview();
        creditReview.setApplication(savedApp);
        creditReview.setCreditOfficer(officerUser);
        creditReview.setDecision(officerDecision);
        creditReview.setComments("Credit officer comments.");
        creditReview.setReviewedAt(LocalDateTime.now());
        creditReviewRepository.save(creditReview);

        return savedApp;
    }

    @Test
    public void testGetQueue_Manager_Success() throws Exception {
        String mgrToken = createUserAndGetToken("mgr_queue@example.com", "MANAGER");
        createUserAndGetToken("off_queue@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_mgr_queue@example.com", "CUSTOMER");

        LoanApplication app = createReadyForManagerApp("cust_mgr_queue@example.com", "off_queue@example.com", "ELIGIBLE", "RECOMMEND");

        mockMvc.perform(get("/api/v1/manager-reviews/queue")
                .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].applicationId").value(app.getId()))
                .andExpect(jsonPath("$.content[0].creditOfficerRecommendation").value("RECOMMEND"));
    }

    @Test
    public void testGetQueue_CreditOfficer_Forbidden() throws Exception {
        String officerToken = createUserAndGetToken("off_no_mgr@example.com", "CREDIT_OFFICER");

        mockMvc.perform(get("/api/v1/manager-reviews/queue")
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetReviewDetails_Success() throws Exception {
        String mgrToken = createUserAndGetToken("mgr_details@example.com", "MANAGER");
        createUserAndGetToken("off_details@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_details2@example.com", "CUSTOMER");

        LoanApplication app = createReadyForManagerApp("cust_details2@example.com", "off_details@example.com", "ELIGIBLE", "RECOMMEND");

        mockMvc.perform(get("/api/v1/manager-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + mgrToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.id").value(app.getId()))
                .andExpect(jsonPath("$.creditOfficerReview.decision").value("RECOMMEND"));
    }

    @Test
    public void testCreateReview_Approve_Success_CreatesLoan() throws Exception {
        String mgrToken = createUserAndGetToken("mgr_create@example.com", "MANAGER");
        createUserAndGetToken("off_create@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_create2@example.com", "CUSTOMER");

        LoanApplication app = createReadyForManagerApp("cust_create2@example.com", "off_create@example.com", "ELIGIBLE", "RECOMMEND");

        CreateManagerReviewRequest req = new CreateManagerReviewRequest("APPROVE", "Manager approves loan based on officer recommendation.");

        mockMvc.perform(post("/api/v1/manager-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + mgrToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(app.getId()))
                .andExpect(jsonPath("$.decision").value("APPROVE"))
                .andExpect(jsonPath("$.applicationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.loanId").value(notNullValue()));

        List<ManagerReview> reviews = managerReviewRepository.findByApplicationIdOrderByReviewedAtDesc(app.getId());
        assertFalse(reviews.isEmpty());
        assertEquals("APPROVE", reviews.get(0).getDecision());

        Optional<Loan> loanOpt = loanRepository.findByApplicationId(app.getId());
        assertTrue(loanOpt.isPresent());
        assertEquals("APPROVED", loanOpt.get().getStatus());
    }

    @Test
    public void testCreateReview_DuplicateReturnsConflict() throws Exception {
        String mgrToken = createUserAndGetToken("mgr_dup@example.com", "MANAGER");
        createUserAndGetToken("off_dup@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_dup2@example.com", "CUSTOMER");

        LoanApplication app = createReadyForManagerApp("cust_dup2@example.com", "off_dup@example.com", "ELIGIBLE", "RECOMMEND");

        CreateManagerReviewRequest req1 = new CreateManagerReviewRequest("APPROVE", "First manager approval.");
        mockMvc.perform(post("/api/v1/manager-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + mgrToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        CreateManagerReviewRequest req2 = new CreateManagerReviewRequest("REJECT", "Second manager decision should conflict.");
        mockMvc.perform(post("/api/v1/manager-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + mgrToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testCreateReview_InvalidDecision_ReturnsBadRequest() throws Exception {
        String mgrToken = createUserAndGetToken("mgr_inv@example.com", "MANAGER");
        createUserAndGetToken("off_inv@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_inv2@example.com", "CUSTOMER");

        LoanApplication app = createReadyForManagerApp("cust_inv2@example.com", "off_inv@example.com", "ELIGIBLE", "RECOMMEND");

        CreateManagerReviewRequest req = new CreateManagerReviewRequest("INVALID_STRING", "Invalid decision string comment.");

        mockMvc.perform(post("/api/v1/manager-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + mgrToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateReview_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/manager-reviews/applications/1")
                .header("Authorization", "Bearer invalid_jwt_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"APPROVE\",\"comments\":\"Unauthenticated request\"}"))
                .andExpect(status().isUnauthorized());
    }
}

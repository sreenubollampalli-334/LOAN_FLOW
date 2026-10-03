package com.loanflow.review.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.audit.repository.AuditLogRepository;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.outbox.repository.OutboxEventRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.review.dto.CreateCreditReviewRequest;
import com.loanflow.review.entity.CreditReview;
import com.loanflow.review.repository.CreditReviewRepository;
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

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
public class CreditReviewControllerIntegrationTest {

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
    private EligibilityAssessmentRepository eligibilityAssessmentRepository;

    @Autowired
    private CreditReviewRepository creditReviewRepository;

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

        testProduct = loanProductRepository.findByName("Credit Review Product Test")
                .orElseGet(() -> {
                    LoanProduct product = new LoanProduct();
                    product.setName("Credit Review Product Test");
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
        regRequest.setFirstName("Review");
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

    private LoanApplication createSubmittedApplicationWithAssessment(String userEmail, String eligibilityDecision) {
        User user = userRepository.findByEmail(userEmail).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).get();

        LoanApplication app = new LoanApplication();
        app.setCustomer(profile);
        app.setProduct(testProduct);
        app.setRequestedAmount(new BigDecimal("10000.00"));
        app.setTermMonths(24);
        app.setPurpose("Consolidation");
        app.setStatus(ApplicationStatus.SUBMITTED.name());

        LoanApplication savedApp = loanApplicationRepository.save(app);

        EligibilityAssessment assessment = new EligibilityAssessment();
        assessment.setApplication(savedApp);
        assessment.setDecision(eligibilityDecision);
        assessment.setRulesetVersion("1.0.0");
        assessment.setInputSnapshot("{}");
        assessment.setAssessedAt(LocalDateTime.now());
        eligibilityAssessmentRepository.save(assessment);

        return savedApp;
    }

    @Test
    public void testGetQueue_CreditOfficer_Success() throws Exception {
        String officerToken = createUserAndGetToken("officer_queue@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_queue@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationWithAssessment("cust_queue@example.com", "ELIGIBLE");

        mockMvc.perform(get("/api/v1/credit-reviews/queue")
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.content[0].applicationId").value(app.getId()))
                .andExpect(jsonPath("$.content[0].eligibilityDecision").value("ELIGIBLE"));
    }

    @Test
    public void testGetQueue_Customer_Forbidden() throws Exception {
        String custToken = createUserAndGetToken("cust_no_queue@example.com", "CUSTOMER");

        mockMvc.perform(get("/api/v1/credit-reviews/queue")
                .header("Authorization", "Bearer " + custToken))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetReviewDetails_Success() throws Exception {
        String officerToken = createUserAndGetToken("officer_details@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_details@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationWithAssessment("cust_details@example.com", "ELIGIBLE");

        mockMvc.perform(get("/api/v1/credit-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application.id").value(app.getId()))
                .andExpect(jsonPath("$.customer.email").value("cust_details@example.com"))
                .andExpect(jsonPath("$.eligibilityAssessment.decision").value("ELIGIBLE"));
    }

    @Test
    public void testGetReviewDetails_MissingApplication_ReturnsNotFound() throws Exception {
        String officerToken = createUserAndGetToken("officer_missing@example.com", "CREDIT_OFFICER");

        mockMvc.perform(get("/api/v1/credit-reviews/applications/99999")
                .header("Authorization", "Bearer " + officerToken))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCreateReview_Success() throws Exception {
        String officerToken = createUserAndGetToken("officer_create@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_create@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationWithAssessment("cust_create@example.com", "ELIGIBLE");

        CreateCreditReviewRequest req = new CreateCreditReviewRequest("RECOMMEND", "Application meets credit standards and criteria.");

        mockMvc.perform(post("/api/v1/credit-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + officerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(app.getId()))
                .andExpect(jsonPath("$.decision").value("RECOMMEND"))
                .andExpect(jsonPath("$.creditOfficerEmail").value("officer_create@example.com"));

        List<CreditReview> reviews = creditReviewRepository.findByApplicationIdOrderByReviewedAtDesc(app.getId());
        assertFalse(reviews.isEmpty());
        assertEquals("RECOMMEND", reviews.get(0).getDecision());
    }

    @Test
    public void testCreateReview_DuplicateReturnsConflict() throws Exception {
        String officerToken = createUserAndGetToken("officer_dup@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_dup@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationWithAssessment("cust_dup@example.com", "ELIGIBLE");

        CreateCreditReviewRequest req1 = new CreateCreditReviewRequest("RECOMMEND", "First review recommendation comment.");
        mockMvc.perform(post("/api/v1/credit-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + officerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req1)))
                .andExpect(status().isCreated());

        CreateCreditReviewRequest req2 = new CreateCreditReviewRequest("DO_NOT_RECOMMEND", "Second review should fail conflict.");
        mockMvc.perform(post("/api/v1/credit-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + officerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req2)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testCreateReview_InvalidDecision_ReturnsBadRequest() throws Exception {
        String officerToken = createUserAndGetToken("officer_invalid@example.com", "CREDIT_OFFICER");
        createUserAndGetToken("cust_invalid@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationWithAssessment("cust_invalid@example.com", "ELIGIBLE");

        CreateCreditReviewRequest req = new CreateCreditReviewRequest("INVALID_DECISION", "This decision string is invalid.");

        mockMvc.perform(post("/api/v1/credit-reviews/applications/" + app.getId())
                .header("Authorization", "Bearer " + officerToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreateReview_Unauthenticated_ReturnsUnauthorized() throws Exception {
        mockMvc.perform(post("/api/v1/credit-reviews/applications/1")
                .header("Authorization", "Bearer invalid_jwt_token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"RECOMMEND\",\"comments\":\"Unauthenticated request\"}"))
                .andExpect(status().isUnauthorized());
    }
}

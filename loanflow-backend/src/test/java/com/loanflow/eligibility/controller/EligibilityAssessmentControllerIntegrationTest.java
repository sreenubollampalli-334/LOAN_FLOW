package com.loanflow.eligibility.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.eligibility.entity.EligibilityAssessment;
import com.loanflow.eligibility.repository.EligibilityAssessmentRepository;
import com.loanflow.eligibility.repository.EligibilityReasonCodeRepository;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
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
public class EligibilityAssessmentControllerIntegrationTest {

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
    private EligibilityReasonCodeRepository eligibilityReasonCodeRepository;

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
        if (roleRepository.findByName("MANAGER").isEmpty()) {
            Role role = new Role();
            role.setName("MANAGER");
            roleRepository.save(role);
        }

        testProduct = loanProductRepository.findByName("Personal Loan Test")
                .orElseGet(() -> {
                    LoanProduct product = new LoanProduct();
                    product.setName("Personal Loan Test");
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
        regRequest.setFirstName("Eligible");
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

    private LoanApplication createSubmittedApplicationForCustomer(String userEmail) {
        User user = userRepository.findByEmail(userEmail).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).get();

        LoanApplication app = new LoanApplication();
        app.setCustomer(profile);
        app.setProduct(testProduct);
        app.setRequestedAmount(new BigDecimal("10000.00"));
        app.setTermMonths(24);
        app.setPurpose("Debt Consolidation");
        app.setStatus(ApplicationStatus.SUBMITTED.name());

        return loanApplicationRepository.save(app);
    }

    @Test
    public void testAssessEligibility_Success() throws Exception {
        String token = createUserAndGetToken("customer_eligible1@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationForCustomer("customer_eligible1@example.com");

        mockMvc.perform(post("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.applicationId").value(app.getId()))
                .andExpect(jsonPath("$.decision").value("ELIGIBLE"))
                .andExpect(jsonPath("$.isDemo").value(true))
                .andExpect(jsonPath("$.demoDisclaimer", containsString("Demo eligibility checks passed")))
                .andExpect(jsonPath("$.rulesetVersion").value("1.0.0"));

        List<EligibilityAssessment> assessments = eligibilityAssessmentRepository.findByApplicationIdOrderByAssessedAtDesc(app.getId());
        assertFalse(assessments.isEmpty());
        assertEquals("ELIGIBLE", assessments.get(0).getDecision());
    }

    @Test
    public void testAssessEligibility_DraftApplication_ReturnsConflict() throws Exception {
        String token = createUserAndGetToken("customer_draft@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationForCustomer("customer_draft@example.com");
        app.setStatus(ApplicationStatus.DRAFT.name());
        loanApplicationRepository.save(app);

        mockMvc.perform(post("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Application must be in SUBMITTED status")));
    }

    @Test
    public void testAssessEligibility_OtherCustomerApplication_ReturnsNotFound() throws Exception {
        String token1 = createUserAndGetToken("owner_cust@example.com", "CUSTOMER");
        String token2 = createUserAndGetToken("other_cust@example.com", "CUSTOMER");

        LoanApplication app = createSubmittedApplicationForCustomer("owner_cust@example.com");

        mockMvc.perform(post("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token2))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testAssessEligibility_MissingApplication_ReturnsNotFound() throws Exception {
        String token = createUserAndGetToken("customer_missing@example.com", "CUSTOMER");

        mockMvc.perform(post("/api/v1/loan-applications/99999/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUnauthenticated() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/1/eligibility-assessments")
                .header("Authorization", "Bearer invalid_jwt_token"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testWrongRole_ReturnsForbidden() throws Exception {
        String token = createUserAndGetToken("manager_user@example.com", "MANAGER");

        mockMvc.perform(post("/api/v1/loan-applications/1/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testGetAssessmentHistory_Success() throws Exception {
        String token = createUserAndGetToken("history_cust@example.com", "CUSTOMER");
        LoanApplication app = createSubmittedApplicationForCustomer("history_cust@example.com");

        mockMvc.perform(post("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/loan-applications/" + app.getId() + "/eligibility-assessments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }
}

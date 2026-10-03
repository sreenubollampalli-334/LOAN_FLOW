package com.loanflow.loan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.loan.dto.CreateLoanApplicationRequest;
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
import com.loanflow.security.UserDetailsImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Set;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
public class LoanApplicationIntegrationTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
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

    private User testUser;
    private CustomerProfile testProfile;
    private LoanProduct testProduct;
    private LoanApplication testApplication;
    private UserDetailsImpl userDetails;

    @BeforeEach
    public void setUp() {
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseGet(() -> {
            Role r = new Role();
            r.setName("CUSTOMER");
            return roleRepository.save(r);
        });

        testUser = new User();
        testUser.setEmail("apptest@example.com");
        testUser.setPasswordHash("hash");
        testUser.setIsActive(true);
        testUser.setRoles(Set.of(customerRole));
        testUser = userRepository.save(testUser);
        userDetails = new UserDetailsImpl(testUser);

        testProfile = new CustomerProfile();
        testProfile.setUser(testUser);
        testProfile.setFirstName("App");
        testProfile.setLastName("Test");
        testProfile.setDob(LocalDate.of(1990, 1, 1));
        testProfile.setPhone("1234567890");
        testProfile = customerProfileRepository.save(testProfile);

        testProduct = new LoanProduct();
        testProduct.setName("Test Loan Product " + System.currentTimeMillis());
        testProduct.setMinAmount(new BigDecimal("1000"));
        testProduct.setMaxAmount(new BigDecimal("50000"));
        testProduct.setInterestRate(new BigDecimal("0.05"));
        testProduct.setMinTermMonths(6);
        testProduct.setMaxTermMonths(60);
        testProduct.setIsActive(true);
        testProduct = loanProductRepository.save(testProduct);

        testApplication = new LoanApplication();
        testApplication.setCustomer(testProfile);
        testApplication.setProduct(testProduct);
        testApplication.setRequestedAmount(new BigDecimal("5000"));
        testApplication.setTermMonths(12);
        testApplication.setStatus(ApplicationStatus.DRAFT.name());
        testApplication = loanApplicationRepository.save(testApplication);
    }

    @Test
    public void testCreateDraft_Success() throws Exception {
        CreateLoanApplicationRequest req = new CreateLoanApplicationRequest();
        req.setProductId(testProduct.getId());
        req.setRequestedAmount(new BigDecimal("10000"));
        req.setTermMonths(24);
        req.setPurpose("Education");

        mockMvc.perform(post("/api/v1/loan-applications")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.productId").value(testProduct.getId()))
                .andExpect(jsonPath("$.status").value("DRAFT"));
    }

    @Test
    public void testCreateDraft_InvalidAmount() throws Exception {
        CreateLoanApplicationRequest req = new CreateLoanApplicationRequest();
        req.setProductId(testProduct.getId());
        req.setRequestedAmount(new BigDecimal("500")); // Below min
        req.setTermMonths(24);

        mockMvc.perform(post("/api/v1/loan-applications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testGetMyApplications() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications")
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray())
                .andExpect(jsonPath("$.content[0].id").value(testApplication.getId()));
    }

    @Test
    public void testGetMyApplicationById() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications/" + testApplication.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(testApplication.getId()));
    }

    @Test
    public void testGetMyApplicationById_WrongOwnerReturnsNotFound() throws Exception {
        User otherUser = new User();
        otherUser.setEmail("another@example.com");
        otherUser.setPasswordHash("hash");
        otherUser.setIsActive(true);
        Role customerRole = roleRepository.findByName("CUSTOMER").orElseThrow();
        otherUser.setRoles(Set.of(customerRole));
        otherUser = userRepository.save(otherUser);
        
        CustomerProfile otherProfile = new CustomerProfile();
        otherProfile.setUser(otherUser);
        otherProfile.setFirstName("Other");
        otherProfile.setLastName("User");
        otherProfile.setDob(LocalDate.of(1990, 1, 1));
        otherProfile.setPhone("0987654321");
        customerProfileRepository.save(otherProfile);

        UserDetailsImpl otherUserDetails = new UserDetailsImpl(otherUser);

        mockMvc.perform(get("/api/v1/loan-applications/" + testApplication.getId())
                        .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(otherUserDetails)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testUpdateDraft_Success() throws Exception {
        com.loanflow.loan.dto.UpdateLoanApplicationRequest req = new com.loanflow.loan.dto.UpdateLoanApplicationRequest();
        req.setRequestedAmount(new BigDecimal("15000"));
        req.setTermMonths(36);
        req.setPurpose("Home Renovation");

        mockMvc.perform(put("/api/v1/loan-applications/" + testApplication.getId())
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAmount").value(15000))
                .andExpect(jsonPath("$.termMonths").value(36))
                .andExpect(jsonPath("$.purpose").value("Home Renovation"));
    }

    @Test
    public void testSubmitApplication_Success() throws Exception {
        mockMvc.perform(post("/api/v1/loan-applications/" + testApplication.getId() + "/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUBMITTED"));
    }

    @Test
    public void testSubmitApplication_RepeatedReturns409() throws Exception {
        // First submit should succeed
        mockMvc.perform(post("/api/v1/loan-applications/" + testApplication.getId() + "/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isOk());

        // Second submit should fail with 409 Conflict
        mockMvc.perform(post("/api/v1/loan-applications/" + testApplication.getId() + "/submit")
                .with(org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user(userDetails)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testUnauthenticatedAccess() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications"))
                .andExpect(status().isUnauthorized());
    }
    
    @Test
    @WithMockUser(username = "officer@example.com", roles = {"CREDIT_OFFICER"})
    public void testWrongRoleAccess() throws Exception {
        mockMvc.perform(get("/api/v1/loan-applications"))
                .andExpect(status().isForbidden());
    }
}

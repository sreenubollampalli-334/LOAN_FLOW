package com.loanflow.loan.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

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
public class LoanControllerIntegrationTest {

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
    private RepaymentScheduleRepository repaymentScheduleRepository;

    private ObjectMapper objectMapper;

    private String customerToken1;
    private String customerToken2;
    private String adminToken;
    private String managerToken;

    private CustomerProfile customerProfile1;
    private CustomerProfile customerProfile2;
    private Loan customer1Loan;
    private Loan customer2Loan;

    @BeforeEach
    void setUp() throws Exception {
        objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        ensureRole("ADMIN");
        ensureRole("CUSTOMER");
        ensureRole("MANAGER");

        String uid = java.util.UUID.randomUUID().toString().substring(0, 8);

        customerToken1 = createUserAndGetToken("cust1_" + uid + "@test.com", "CUSTOMER");
        customerToken2 = createUserAndGetToken("cust2_" + uid + "@test.com", "CUSTOMER");
        adminToken = createUserAndGetToken("admin_" + uid + "@test.com", "ADMIN");
        managerToken = createUserAndGetToken("mgr_" + uid + "@test.com", "MANAGER");

        User u1 = userRepository.findByEmail("cust1_" + uid + "@test.com").orElseThrow();
        customerProfile1 = customerProfileRepository.findByUserId(u1.getId()).orElseThrow();

        User u2 = userRepository.findByEmail("cust2_" + uid + "@test.com").orElseThrow();
        customerProfile2 = customerProfileRepository.findByUserId(u2.getId()).orElseThrow();

        LoanProduct product = loanProductRepository.findByName("Loan API Test Product")
                .orElseGet(() -> {
                    LoanProduct p = new LoanProduct();
                    p.setName("Loan API Test Product");
                    p.setDescription("Test Product");
                    p.setMinAmount(new BigDecimal("1000.00"));
                    p.setMaxAmount(new BigDecimal("50000.00"));
                    p.setInterestRate(new BigDecimal("0.1200"));
                    p.setMinTermMonths(6);
                    p.setMaxTermMonths(60);
                    p.setIsActive(true);
                    return loanProductRepository.save(p);
                });

        // App & Loan for Customer 1
        LoanApplication app1 = new LoanApplication();
        app1.setCustomer(customerProfile1);
        app1.setProduct(product);
        app1.setRequestedAmount(new BigDecimal("12000.00"));
        app1.setTermMonths(12);
        app1.setStatus(ApplicationStatus.APPROVED.name());
        app1 = loanApplicationRepository.save(app1);

        customer1Loan = new Loan();
        customer1Loan.setApplication(app1);
        customer1Loan.setCustomer(customerProfile1);
        customer1Loan.setProduct(product);
        customer1Loan.setPrincipalAmount(new BigDecimal("12000.00"));
        customer1Loan.setTermMonths(12);
        customer1Loan.setInterestRate(new BigDecimal("0.1200"));
        customer1Loan.setInterestCalculationMethod("FLAT");
        customer1Loan.setStatus("DISBURSED");
        customer1Loan = loanRepository.save(customer1Loan);

        // Schedule for Customer 1 Loan
        RepaymentSchedule s1 = new RepaymentSchedule();
        s1.setLoan(customer1Loan);
        s1.setInstallmentNumber(1);
        s1.setDueDate(LocalDate.of(2026, 2, 15));
        s1.setPrincipalAmount(new BigDecimal("1000.0000"));
        s1.setInterestAmount(new BigDecimal("120.0000"));
        s1.setTotalAmount(new BigDecimal("1120.0000"));
        s1.setStatus("PENDING");
        repaymentScheduleRepository.save(s1);

        RepaymentSchedule s2 = new RepaymentSchedule();
        s2.setLoan(customer1Loan);
        s2.setInstallmentNumber(2);
        s2.setDueDate(LocalDate.of(2026, 3, 15));
        s2.setPrincipalAmount(new BigDecimal("1000.0000"));
        s2.setInterestAmount(new BigDecimal("120.0000"));
        s2.setTotalAmount(new BigDecimal("1120.0000"));
        s2.setStatus("PENDING");
        repaymentScheduleRepository.save(s2);

        // App & Loan for Customer 2
        LoanApplication app2 = new LoanApplication();
        app2.setCustomer(customerProfile2);
        app2.setProduct(product);
        app2.setRequestedAmount(new BigDecimal("5000.00"));
        app2.setTermMonths(6);
        app2.setStatus(ApplicationStatus.APPROVED.name());
        app2 = loanApplicationRepository.save(app2);

        customer2Loan = new Loan();
        customer2Loan.setApplication(app2);
        customer2Loan.setCustomer(customerProfile2);
        customer2Loan.setProduct(product);
        customer2Loan.setPrincipalAmount(new BigDecimal("5000.00"));
        customer2Loan.setTermMonths(6);
        customer2Loan.setInterestRate(new BigDecimal("0.1200"));
        customer2Loan.setInterestCalculationMethod("FLAT");
        customer2Loan.setStatus("DISBURSED");
        customer2Loan = loanRepository.save(customer2Loan);
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
        regRequest.setFirstName("LoanApi");
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
    @DisplayName("GET /api/v1/loans/my-loans - Customer retrieves only owned loans")
    void getMyLoans_customerSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/loans/my-loans")
                        .header("Authorization", "Bearer " + customerToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(1)))
                .andExpect(jsonPath("$.content[0].id").value(customer1Loan.getId()))
                .andExpect(jsonPath("$.content[0].principalAmount").value(12000.0));
    }

    @Test
    @DisplayName("GET /api/v1/loans/my-loans - Admin returns 403 (Customer role required)")
    void getMyLoans_adminForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/loans/my-loans")
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/loans/my-loans - Unauthenticated returns 401")
    void getMyLoans_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/loans/my-loans"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId} - Customer can view own loan")
    void getLoanById_customerOwnerSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId())
                        .header("Authorization", "Bearer " + customerToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer1Loan.getId()))
                .andExpect(jsonPath("$.status").value("DISBURSED"));
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId} - Customer cannot view another customer's loan (returns 404)")
    void getLoanById_customerUnownedReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer2Loan.getId())
                        .header("Authorization", "Bearer " + customerToken1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId} - Admin & Manager can view any loan")
    void getLoanById_adminAndManagerSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId())
                        .header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer1Loan.getId()));

        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId())
                        .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(customer1Loan.getId()));
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId}/schedule - Customer can view own repayment schedule ordered by installment")
    void getLoanSchedule_customerOwnerSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId() + "/schedule")
                        .header("Authorization", "Bearer " + customerToken1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].installmentNumber").value(1))
                .andExpect(jsonPath("$[1].installmentNumber").value(2));
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId}/schedule - Customer cannot view another customer's schedule (returns 404)")
    void getLoanSchedule_customerUnownedReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer2Loan.getId() + "/schedule")
                        .header("Authorization", "Bearer " + customerToken1))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("POST /api/v1/loans/calculate-emi - EMI calculation preview succeeds without persisting data")
    void calculateEmi_successPreview() throws Exception {
        EmiCalculationRequest request = new EmiCalculationRequest(
                new BigDecimal("15000.0000"),
                new BigDecimal("0.1000"),
                12,
                "FLAT"
        );

        long loanCountBefore = loanRepository.count();
        long scheduleCountBefore = repaymentScheduleRepository.count();

        mockMvc.perform(post("/api/v1/loans/calculate-emi")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalAmount").value(15000.0))
                .andExpect(jsonPath("$.totalInterest").value(1500.0))
                .andExpect(jsonPath("$.totalRepayment").value(16500.0))
                .andExpect(jsonPath("$.breakdown", hasSize(12)));

        assertEquals(loanCountBefore, loanRepository.count());
        assertEquals(scheduleCountBefore, repaymentScheduleRepository.count());
    }

    @Test
    @DisplayName("POST /api/v1/loans/calculate-emi - Invalid request (zero principal) returns 400 Bad Request")
    void calculateEmi_invalidRequestReturns400() throws Exception {
        EmiCalculationRequest request = new EmiCalculationRequest(
                BigDecimal.ZERO,
                new BigDecimal("0.1000"),
                12,
                "FLAT"
        );

        mockMvc.perform(post("/api/v1/loans/calculate-emi")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId}/repayment-summary - Customer gets own loan summary")
    void getLoanRepaymentSummary_success() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId() + "/repayment-summary")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.loanId").value(customer1Loan.getId()))
                .andExpect(jsonPath("$.totalScheduledAmount").value(2240.0))
                .andExpect(jsonPath("$.totalPaid").value(0.0))
                .andExpect(jsonPath("$.outstandingBalance").value(2240.0))
                .andExpect(jsonPath("$.pendingInstallments").value(2))
                .andExpect(jsonPath("$.paidInstallments").value(0));
    }

    @Test
    @DisplayName("GET /api/v1/loans/{loanId}/repayment-summary - Customer cannot get another customer's summary")
    void getLoanRepaymentSummary_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/loans/" + customer1Loan.getId() + "/repayment-summary")
                        .header("Authorization", "Bearer " + customerToken2)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("GET /api/v1/loans/repayment-summary - Customer gets aggregate overview")
    void getCustomerRepaymentOverview_success() throws Exception {
        mockMvc.perform(get("/api/v1/loans/repayment-summary")
                        .header("Authorization", "Bearer " + customerToken1)
                        .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalActiveLoans").value(1))
                .andExpect(jsonPath("$.totalOutstandingBalance").value(2240.0))
                .andExpect(jsonPath("$.totalPaidAmount").value(0.0))
                .andExpect(jsonPath("$.loanSummaries", hasSize(1)))
                .andExpect(jsonPath("$.loanSummaries[0].loanId").value(customer1Loan.getId()));
    }
}

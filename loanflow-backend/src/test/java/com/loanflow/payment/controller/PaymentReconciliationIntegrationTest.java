package com.loanflow.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.loanflow.auth.dto.AuthResponse;
import com.loanflow.auth.dto.RegistrationRequest;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.entity.LoanApplication;
import com.loanflow.loan.enums.ApplicationStatus;
import com.loanflow.loan.repository.LoanApplicationRepository;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.payment.dto.PaymentRequest;
import com.loanflow.payment.entity.Payment;
import com.loanflow.payment.entity.PaymentAllocation;
import com.loanflow.payment.repository.PaymentAllocationRepository;
import com.loanflow.payment.repository.PaymentRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.product.repository.LoanProductRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;

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
@org.springframework.transaction.annotation.Transactional
public class PaymentReconciliationIntegrationTest {

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
    private PaymentRepository paymentRepository;

    @Autowired
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private com.loanflow.payment.repository.RefundAllocationRepository refundAllocationRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

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

        testProduct = loanProductRepository.findByName("Personal Loan Test Recon")
                .orElseGet(() -> {
                    LoanProduct product = new LoanProduct();
                    product.setName("Personal Loan Test Recon");
                    product.setDescription("Test Product for Recon");
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
        if (userRepository.findByEmail(email).isPresent()) {
            return generateTokenForExistingUser(email);
        }
        RegistrationRequest regRequest = new RegistrationRequest();
        regRequest.setFirstName("Recon");
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
            throw new IllegalArgumentException("createUserAndGetToken is now only used for CUSTOMER. For others, use .with(user(...))");
        }

        return authResponse.getToken();
    }

    private String generateTokenForExistingUser(String email) throws Exception {
        com.loanflow.auth.dto.LoginRequest loginRequest = new com.loanflow.auth.dto.LoginRequest();
        loginRequest.setEmail(email);
        loginRequest.setPassword("securePassword123");
        
        String responseStr = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(loginRequest)))
                .andReturn().getResponse().getContentAsString();
                
        return objectMapper.readValue(responseStr, AuthResponse.class).getToken();
    }

    private Loan createLoanForCustomer(String userEmail) {
        User user = userRepository.findByEmail(userEmail).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).get();

        LoanApplication app = new LoanApplication();
        app.setCustomer(profile);
        app.setProduct(testProduct);
        app.setRequestedAmount(new BigDecimal("10000.00"));
        app.setTermMonths(24);
        app.setPurpose("Debt Consolidation");
        app.setStatus(ApplicationStatus.APPROVED.name());
        app = loanApplicationRepository.save(app);

        Loan loan = new Loan();
        loan.setApplication(app);
        loan.setCustomer(profile);
        loan.setProduct(testProduct);
        loan.setPrincipalAmount(new BigDecimal("10000.00"));
        loan.setInterestRate(new BigDecimal("0.1000"));
        loan.setInterestCalculationMethod("SIMPLE");
        loan.setTermMonths(24);
        loan.setStatus("DISBURSED");
        return loanRepository.save(loan);
    }

    private void createSchedules(Loan loan) {
        for (int i = 1; i <= 3; i++) {
            RepaymentSchedule schedule = new RepaymentSchedule();
            schedule.setLoan(loan);
            schedule.setInstallmentNumber(i);
            schedule.setDueDate(LocalDate.now().plusMonths(i));
            schedule.setPrincipalAmount(new BigDecimal("100.00"));
            schedule.setInterestAmount(new BigDecimal("10.00"));
            schedule.setTotalAmount(new BigDecimal("110.00"));
            schedule.setStatus("PENDING");
            repaymentScheduleRepository.save(schedule);
        }
    }

    @Test
    public void testReconciliation_Consistent() throws Exception {
        String token = createUserAndGetToken("recon_cons@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("recon_cons@example.com");
        createSchedules(loan);

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payment-reconciliation")
                .with(user("manager_cons").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConsistent").value(true))
                .andExpect(jsonPath("$.totalPayments").value(150.00))
                .andExpect(jsonPath("$.totalOutstanding").value(180.00)); // 330 total - 150 paid = 180
    }

    @Test
    public void testReconciliation_CustomerForbidden() throws Exception {
        String token = createUserAndGetToken("recon_forb@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("recon_forb@example.com");

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payment-reconciliation")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isForbidden());
    }

    @Test
    public void testReconciliation_MissingLoan() throws Exception {
        mockMvc.perform(get("/api/v1/loans/999999/payment-reconciliation")
                .with(user("manager_missing").roles("MANAGER")))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testReconciliation_InconsistentPayment() throws Exception {
        String customerToken = createUserAndGetToken("recon_incons@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("recon_incons@example.com");
        createSchedules(loan);
        
        CustomerProfile profile = loan.getCustomer();
        
        Payment payment = new Payment();
        payment.setLoan(loan);
        payment.setCustomer(profile);
        payment.setAmount(new BigDecimal("100.00")); // Inconsistent: amount is 100
        payment.setUnappliedAmount(new BigDecimal("90.00")); // But unapplied + alloc = 90
        payment.setPaymentDate(LocalDateTime.now());
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setRequestHash("hash");
        payment.setReferenceNumber(UUID.randomUUID().toString());
        payment.setStatus("SUCCESS");
        paymentRepository.save(payment);

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payment-reconciliation")
                .with(user("manager_incons").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConsistent").value(false))
                .andExpect(jsonPath("$.inconsistencies[0]", containsString("accounting amount")));
    }

    @Test
    public void testReconciliation_OverRefundedAllocation() throws Exception {
        String customerToken = createUserAndGetToken("recon_or@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("recon_or@example.com");
        createSchedules(loan);
        
        Payment payment = new Payment();
        payment.setLoan(loan);
        payment.setCustomer(loan.getCustomer());
        payment.setAmount(new BigDecimal("100.00"));
        payment.setUnappliedAmount(new BigDecimal("0.00"));
        payment.setPaymentDate(LocalDateTime.now());
        payment.setIdempotencyKey(UUID.randomUUID().toString());
        payment.setRequestHash("hash");
        payment.setReferenceNumber(UUID.randomUUID().toString());
        payment.setStatus("SUCCESS");
        payment = paymentRepository.saveAndFlush(payment);

        RepaymentSchedule schedule = repaymentScheduleRepository.findByLoanIdAndInstallmentNumber(loan.getId(), 1).get();
        PaymentAllocation pa = new PaymentAllocation();
        pa.setPayment(payment);
        pa.setLoan(loan);
        pa.setRepaymentSchedule(schedule);
        pa.setAllocatedAmount(new BigDecimal("100.00"));
        pa = paymentAllocationRepository.saveAndFlush(pa);

        Payment refund = new Payment();
        refund.setLoan(loan);
        refund.setCustomer(loan.getCustomer());
        refund.setAmount(new BigDecimal("150.00"));
        refund.setUnappliedAmount(new BigDecimal("0.00"));
        refund.setType("REFUND");
        refund.setPaymentDate(LocalDateTime.now());
        refund.setIdempotencyKey(UUID.randomUUID().toString());
        refund.setRequestHash("hash_ref");
        refund.setReferenceNumber(UUID.randomUUID().toString());
        refund.setStatus("SUCCESS");
        refund = paymentRepository.saveAndFlush(refund);

        com.loanflow.payment.entity.RefundAllocation ra = new com.loanflow.payment.entity.RefundAllocation();
        ra.setRefundPayment(refund);
        ra.setOriginalAllocation(pa);
        ra.setLoan(loan);
        ra.setRepaymentSchedule(schedule);
        ra.setRefundedAmount(new BigDecimal("150.00")); // exceeds 100
        refundAllocationRepository.saveAndFlush(ra);

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payment-reconciliation")
                .with(user("manager_or").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConsistent").value(false))
                .andExpect(jsonPath("$.inconsistencies[0]", containsString("exceeds original allocation")));
    }



    @Test
    public void testReconciliation_NoPaymentHistory() throws Exception {
        String customerToken = createUserAndGetToken("recon_nph@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("recon_nph@example.com");
        createSchedules(loan);

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payment-reconciliation")
                .with(user("manager_nph").roles("MANAGER")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isConsistent").value(true))
                .andExpect(jsonPath("$.totalPayments").value(0.00));
    }
}

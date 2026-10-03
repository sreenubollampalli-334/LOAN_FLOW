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
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
public class PaymentControllerIntegrationTest {

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
    private com.loanflow.outbox.repository.OutboxEventRepository outboxEventRepository;

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
        regRequest.setFirstName("Payment");
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

    @Test
    public void testCreatePayment_Success() throws Exception {
        String token = createUserAndGetToken("payment_success@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("payment_success@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.loanId").value(loan.getId()))
                .andExpect(jsonPath("$.amount").value(150.00))
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.referenceNumber").exists())
                .andExpect(jsonPath("$.idempotencyKey").value(idempotencyKey));
        
        java.util.List<com.loanflow.outbox.entity.OutboxEvent> events = outboxEventRepository.findAll();
        org.junit.jupiter.api.Assertions.assertTrue(events.stream().anyMatch(e -> "PAYMENT_RECEIVED".equals(e.getType()) && "Payment".equals(e.getAggregateType())));
    }

    @Test
    public void testCreatePayment_LoanNotDisbursed() throws Exception {
        String token = createUserAndGetToken("payment_not_disbursed@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("payment_not_disbursed@example.com");
        
        // Change status to ACTIVE (not DISBURSED)
        loan.setStatus("ACTIVE");
        loanRepository.save(loan);

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Cannot make a payment on a loan that is not in DISBURSED status")));
                
        java.util.List<com.loanflow.outbox.entity.OutboxEvent> events = outboxEventRepository.findAll();
        org.junit.jupiter.api.Assertions.assertFalse(events.stream().anyMatch(e -> "PAYMENT_RECEIVED".equals(e.getType()) && "Payment".equals(e.getAggregateType())));
    }

    @Test
    public void testCreatePayment_InvalidRequest() throws Exception {
        String token = createUserAndGetToken("payment_invalid@example.com", "CUSTOMER");
        
        PaymentRequest request = new PaymentRequest();
        request.setAmount(new BigDecimal("-10.00")); // invalid amount, missing loanId

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    public void testCreatePayment_CustomerOwnershipViolation() throws Exception {
        String ownerToken = createUserAndGetToken("payment_owner@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("payment_owner@example.com");

        String otherToken = createUserAndGetToken("payment_other@example.com", "CUSTOMER");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + otherToken)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound()); // Service throws ResourceNotFoundException
    }

    @Test
    public void testCreatePayment_Unauthorized() throws Exception {
        PaymentRequest request = new PaymentRequest();
        request.setLoanId(1L);
        request.setAmount(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    public void testCreatePayment_IdempotencySameRequest() throws Exception {
        String token = createUserAndGetToken("payment_idem_same@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("payment_idem_same@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        String idempotencyKey = UUID.randomUUID().toString();

        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String firstResponse = firstResult.getResponse().getContentAsString();

        // Second identical request
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String secondResponse = secondResult.getResponse().getContentAsString();

        assertEquals(firstResponse, secondResponse);
    }

    @Test
    public void testCreatePayment_IdempotencyDifferentRequest() throws Exception {
        String token = createUserAndGetToken("payment_idem_diff@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("payment_idem_diff@example.com");

        PaymentRequest firstRequest = new PaymentRequest();
        firstRequest.setLoanId(loan.getId());
        firstRequest.setAmount(new BigDecimal("150.00"));

        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // Second different request with same idempotency key
        PaymentRequest secondRequest = new PaymentRequest();
        secondRequest.setLoanId(loan.getId());
        secondRequest.setAmount(new BigDecimal("200.00")); // Different amount

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message", containsString("Idempotency conflict")));
    }

    @Test
    public void testGetPaymentHistory() throws Exception {
        String token = createUserAndGetToken("history@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("history@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .param("loanId", loan.getId().toString())
                .param("status", "SUCCESS"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(150.00));
    }

    @Test
    public void testGetPaymentDetails() throws Exception {
        String token = createUserAndGetToken("details@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("details@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("200.00"));
        
        String responseStr = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
                
        Long paymentId = objectMapper.readTree(responseStr).get("id").asLong();

        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(paymentId))
                .andExpect(jsonPath("$.unappliedAmount").value(200.00))
                .andExpect(jsonPath("$.allocations").exists());
                
        String otherToken = createUserAndGetToken("details_other@example.com", "CUSTOMER");
        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
                
        String managerToken = createUserAndGetToken("details_manager@example.com", "MANAGER");
        mockMvc.perform(get("/api/v1/payments/" + paymentId)
                .header("Authorization", "Bearer " + managerToken))
                .andExpect(status().isOk());
    }

    @Test
    public void testGetRefundHistory() throws Exception {
        String token = createUserAndGetToken("refund_hist@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("refund_hist@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("200.00"));
        
        String responseStr = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andReturn().getResponse().getContentAsString();
                
        Long paymentId = objectMapper.readTree(responseStr).get("id").asLong();
        
        com.loanflow.payment.dto.RefundRequest refundRequest = new com.loanflow.payment.dto.RefundRequest();
        refundRequest.setAmount(new BigDecimal("50.00"));
        
        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(refundRequest)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].amount").value(50.00))
                .andExpect(jsonPath("$[0].refundAllocations").exists());
    }

    @Test
    public void testGetLoanPaymentHistory() throws Exception {
        String token = createUserAndGetToken("loan_hist@example.com", "CUSTOMER");
        Loan loan = createLoanForCustomer("loan_hist@example.com");

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("300.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payments")
                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].amount").value(300.00));
                
        String otherToken = createUserAndGetToken("loan_hist_other@example.com", "CUSTOMER");
        mockMvc.perform(get("/api/v1/loans/" + loan.getId() + "/payments")
                .header("Authorization", "Bearer " + otherToken))
                .andExpect(status().isNotFound());
    }
}

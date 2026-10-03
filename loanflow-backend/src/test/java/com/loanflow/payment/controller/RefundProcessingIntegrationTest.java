package com.loanflow.payment.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.payment.dto.PaymentRequest;
import com.loanflow.payment.dto.RefundRequest;
import com.loanflow.payment.entity.Payment;
import com.loanflow.payment.repository.PaymentRepository;
import com.loanflow.payment.repository.RefundAllocationRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import com.loanflow.auth.dto.LoginRequest;
import com.loanflow.auth.dto.AuthResponse;
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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
public class RefundProcessingIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private com.loanflow.product.repository.LoanProductRepository loanProductRepository;

    @Autowired
    private com.loanflow.loan.repository.LoanApplicationRepository loanApplicationRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private RoleRepository roleRepository;

    @Autowired
    private CustomerProfileRepository customerProfileRepository;

    @Autowired
    private LoanRepository loanRepository;

    @Autowired
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private RefundAllocationRepository refundAllocationRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    public void setup() {
        if (!roleRepository.findByName("ROLE_CUSTOMER").isPresent()) {
            Role role = new Role();
            role.setName("ROLE_CUSTOMER");
            roleRepository.save(role);
        }
    }

    private String createUserAndGetToken(String email) throws Exception {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode("password123"));
        user.setIsActive(true);
        Role role = roleRepository.findByName("ROLE_CUSTOMER").get();
        user.getRoles().add(role);
        user = userRepository.save(user);

        CustomerProfile profile = new CustomerProfile();
        profile.setUser(user);
        profile.setFirstName("Test");
        profile.setLastName("User");
        String uniquePhone = "+1" + UUID.randomUUID().toString().replaceAll("-", "").substring(0, 10);
        profile.setPhone(uniquePhone);
        profile.setDob(LocalDate.of(1990, 1, 1));
        customerProfileRepository.save(profile);

        LoginRequest authRequest = new LoginRequest();
        authRequest.setEmail(email);
        authRequest.setPassword("password123");

        MvcResult result = mockMvc.perform(post("/api/v1/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(authRequest)))
                .andReturn();

        AuthResponse authResponse = objectMapper.readValue(result.getResponse().getContentAsString(), AuthResponse.class);
        return authResponse.getToken();
    }

    private Loan createLoanWithSchedules(String email, int numSchedules, BigDecimal scheduleAmount) {
        User user = userRepository.findByEmail(email).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).get();

        com.loanflow.product.entity.LoanProduct product = new com.loanflow.product.entity.LoanProduct();
        product.setName("Test Product " + java.util.UUID.randomUUID().toString());
        product.setDescription("Test");
        product.setMinAmount(new BigDecimal("100.00"));
        product.setMaxAmount(new BigDecimal("100000.00"));
        product.setInterestRate(new BigDecimal("0.0500"));
        product.setMinTermMonths(1);
        product.setMaxTermMonths(120);
        product.setIsActive(true);
        product = loanProductRepository.save(product);

        com.loanflow.loan.entity.LoanApplication app = new com.loanflow.loan.entity.LoanApplication();
        app.setCustomer(profile);
        app.setProduct(product);
        app.setRequestedAmount(scheduleAmount.multiply(new BigDecimal(numSchedules)));
        app.setTermMonths(numSchedules);
        app.setPurpose("Test");
        app.setStatus("APPROVED");
        app = loanApplicationRepository.save(app);

        Loan loan = new Loan();
        loan.setApplication(app);
        loan.setProduct(product);
        loan.setCustomer(profile);
        loan.setPrincipalAmount(scheduleAmount.multiply(new BigDecimal(numSchedules)));
        loan.setInterestRate(new BigDecimal("0.0500"));
        loan.setTermMonths(numSchedules);
        loan.setStatus("APPROVED");
        loan.setInterestCalculationMethod("FLAT");
        loan = loanRepository.save(loan);

        for (int i = 1; i <= numSchedules; i++) {
            RepaymentSchedule rs = new RepaymentSchedule();
            rs.setLoan(loan);
            rs.setInstallmentNumber(i);
            rs.setDueDate(LocalDate.now().plusMonths(i));
            rs.setPrincipalAmount(scheduleAmount);
            rs.setInterestAmount(BigDecimal.ZERO);
            rs.setTotalAmount(scheduleAmount);
            rs.setStatus("PENDING");
            repaymentScheduleRepository.save(rs);
        }

        return loan;
    }

    private Long createPayment(String token, Long loanId, BigDecimal amount) throws Exception {
        PaymentRequest pr = new PaymentRequest();
        pr.setLoanId(loanId);
        pr.setAmount(amount);

        MvcResult result = mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(pr)))
                .andExpect(status().isCreated())
                .andReturn();
        
        String content = result.getResponse().getContentAsString();
        // Extract id manually to avoid creating another DTO
        String idStr = content.split("\"id\":")[1].split(",")[0];
        return Long.parseLong(idStr);
    }

    @Test
    public void testFullRefundOfAllocatedAmount() throws Exception {
        String email = "refund_full_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("100.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<Payment> payments = paymentRepository.findByLoanId(loan.getId());
        assertEquals(2, payments.size());
        
        Payment original = paymentRepository.findById(paymentId).get();
        assertEquals(0, new BigDecimal("0.00").compareTo(original.getRefundedUnappliedAmount()));

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PENDING", schedules.get(0).getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(schedules.get(0).getAmountRefunded()));
        assertEquals(0, new BigDecimal("100.00").compareTo(schedules.get(0).getAmountPaid()));
    }

    @Test
    public void testPartialRefund() throws Exception {
        String email = "refund_part_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("100.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("40.00"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PARTIALLY_PAID", schedules.get(0).getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(schedules.get(0).getAmountRefunded()));
        assertEquals(0, new BigDecimal("100.00").compareTo(schedules.get(0).getAmountPaid()));
    }

    @Test
    public void testRefundOfUnappliedAmount() throws Exception {
        String email = "refund_unapp_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("150.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Payment original = paymentRepository.findById(paymentId).get();
        assertEquals(0, new BigDecimal("50.00").compareTo(original.getRefundedUnappliedAmount()));
        
        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PAID", schedules.get(0).getStatus());
        assertEquals(0, new BigDecimal("0.00").compareTo(schedules.get(0).getAmountRefunded() != null ? schedules.get(0).getAmountRefunded() : BigDecimal.ZERO));
    }

    @Test
    public void testOverRefundRejection() throws Exception {
        String email = "refund_over_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("100.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testRefundARefundRejection() throws Exception {
        String email = "refund_refund_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("100.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("50.00"));

        MvcResult result = mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();
                
        String content = result.getResponse().getContentAsString();
        String refundIdStr = content.split("\"id\":")[1].split(",")[0];
        Long refundId = Long.parseLong(refundIdStr);

        mockMvc.perform(post("/api/v1/payments/" + refundId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    public void testCrossCustomerAccessRejection() throws Exception {
        String email1 = "refund_cross1_" + UUID.randomUUID().toString() + "@example.com";
        String token1 = createUserAndGetToken(email1);
        Loan loan = createLoanWithSchedules(email1, 1, new BigDecimal("100.00"));
        Long paymentId = createPayment(token1, loan.getId(), new BigDecimal("100.00"));

        String email2 = "refund_cross2_" + UUID.randomUUID().toString() + "@example.com";
        String token2 = createUserAndGetToken(email2);

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("50.00"));

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token2)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testDuplicateRefundRequestIdempotency() throws Exception {
        String email = "refund_dup_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        Long paymentId = createPayment(token, loan.getId(), new BigDecimal("100.00"));

        RefundRequest request = new RefundRequest();
        request.setAmount(new BigDecimal("50.00"));
        String idempotencyKey = UUID.randomUUID().toString();

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/v1/payments/" + paymentId + "/refunds")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());
                
        List<Payment> refunds = paymentRepository.findByLoanId(loan.getId());
        assertEquals(2, refunds.size()); // 1 original + 1 refund
    }
}

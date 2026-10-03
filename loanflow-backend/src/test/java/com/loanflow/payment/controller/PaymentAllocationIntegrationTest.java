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
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
public class PaymentAllocationIntegrationTest {

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

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

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

        testProduct = loanProductRepository.findByName("Personal Loan Alloc Test")
                .orElseGet(() -> {
                    LoanProduct product = new LoanProduct();
                    product.setName("Personal Loan Alloc Test");
                    product.setDescription("Test Product for Allocations");
                    product.setMinAmount(new BigDecimal("1000.00"));
                    product.setMaxAmount(new BigDecimal("50000.00"));
                    product.setInterestRate(new BigDecimal("0.1000"));
                    product.setMinTermMonths(6);
                    product.setMaxTermMonths(60);
                    product.setIsActive(true);
                    return loanProductRepository.save(product);
                });
    }

    private static final java.util.concurrent.atomic.AtomicInteger phoneCounter = new java.util.concurrent.atomic.AtomicInteger(100000000);

    private String createUserAndGetToken(String email) throws Exception {
        RegistrationRequest regRequest = new RegistrationRequest();
        regRequest.setFirstName("Alloc");
        regRequest.setLastName("User");
        regRequest.setEmail(email);
        regRequest.setPassword("securePassword123");
        regRequest.setDob(LocalDate.of(1990, 1, 1));
        regRequest.setPhone(String.valueOf(9000000000L + phoneCounter.incrementAndGet())); // Unique 10-digit phone

        String responseStr = mockMvc.perform(post("/api/v1/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(regRequest)))
                .andReturn().getResponse().getContentAsString();

        AuthResponse authResponse = objectMapper.readValue(responseStr, AuthResponse.class);
        return authResponse.getToken();
    }

    private Loan createLoanWithSchedules(String userEmail, int numberOfInstallments, BigDecimal installAmount) {
        User user = userRepository.findByEmail(userEmail).get();
        CustomerProfile profile = customerProfileRepository.findByUserId(user.getId()).get();

        LoanApplication app = new LoanApplication();
        app.setCustomer(profile);
        app.setProduct(testProduct);
        app.setRequestedAmount(new BigDecimal("10000.00"));
        app.setTermMonths(numberOfInstallments);
        app.setPurpose("Allocation Test");
        app.setStatus(ApplicationStatus.APPROVED.name());
        app = loanApplicationRepository.save(app);

        Loan loan = new Loan();
        loan.setApplication(app);
        loan.setCustomer(profile);
        loan.setProduct(testProduct);
        loan.setPrincipalAmount(new BigDecimal("10000.00"));
        loan.setInterestRate(new BigDecimal("0.1000"));
        loan.setInterestCalculationMethod("SIMPLE");
        loan.setTermMonths(numberOfInstallments);
        loan.setStatus("DISBURSED");
        loan = loanRepository.save(loan);

        for (int i = 1; i <= numberOfInstallments; i++) {
            RepaymentSchedule rs = new RepaymentSchedule();
            rs.setLoan(loan);
            rs.setInstallmentNumber(i);
            rs.setDueDate(LocalDate.now().plusMonths(i));
            rs.setPrincipalAmount(installAmount.multiply(new BigDecimal("0.8")));
            rs.setInterestAmount(installAmount.multiply(new BigDecimal("0.2")));
            rs.setTotalAmount(installAmount);
            rs.setAmountPaid(BigDecimal.ZERO);
            rs.setAmountRefunded(BigDecimal.ZERO);
            rs.setStatus("PENDING");
            repaymentScheduleRepository.save(rs);
        }
        return loan;
    }

    @Test
    public void testExactPaymentAllocation() throws Exception {
        String email = "exact_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 2, new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("100.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<Payment> payments = paymentRepository.findByLoanId(loan.getId());
        assertEquals(1, payments.size());
        assertEquals(0, new BigDecimal("0.00").compareTo(payments.get(0).getUnappliedAmount()));

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PAID", schedules.get(0).getStatus());
        assertEquals(0, new BigDecimal("100.00").compareTo(schedules.get(0).getAmountPaid()));
        
        assertEquals("PENDING", schedules.get(1).getStatus());
        assertEquals(0, new BigDecimal("0.00").compareTo(schedules.get(1).getAmountPaid()));
        
        Loan updatedLoan = loanRepository.findById(loan.getId()).get();
        assertEquals("DISBURSED", updatedLoan.getStatus(), "Loan should remain DISBURSED as one installment is still PENDING");
    }

    @Test
    public void testPartialPaymentAllocation() throws Exception {
        String email = "partial_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("40.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PARTIALLY_PAID", schedules.get(0).getStatus());
        assertEquals(0, new BigDecimal("40.00").compareTo(schedules.get(0).getAmountPaid()));
        
        Loan updatedLoan = loanRepository.findById(loan.getId()).get();
        assertEquals("DISBURSED", updatedLoan.getStatus());
    }

    @Test
    public void testMultipleInstallmentsAllocation() throws Exception {
        String email = "multi_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 3, new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("250.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PAID", schedules.get(0).getStatus());
        assertEquals("PAID", schedules.get(1).getStatus());
        assertEquals("PARTIALLY_PAID", schedules.get(2).getStatus());
        assertEquals(0, new BigDecimal("50.00").compareTo(schedules.get(2).getAmountPaid()));

        Loan updatedLoan = loanRepository.findById(loan.getId()).get();
        assertEquals("DISBURSED", updatedLoan.getStatus());
    }

    @Test
    public void testOverpaymentAllocation() throws Exception {
        String email = "over_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("150.00"));

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        List<Payment> payments = paymentRepository.findByLoanId(loan.getId());
        assertEquals(0, new BigDecimal("50.00").compareTo(payments.get(0).getUnappliedAmount()));

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        assertEquals("PAID", schedules.get(0).getStatus());

        Loan updatedLoan = loanRepository.findById(loan.getId()).get();
        assertEquals("CLOSED", updatedLoan.getStatus(), "Loan should be CLOSED since all schedules are PAID");
    }

    @Test
    public void testFullPaymentClosesLoan() throws Exception {
        String email = "full_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));

        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("100.00")); // Exact amount

        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        Loan updatedLoan = loanRepository.findById(loan.getId()).get();
        assertEquals("CLOSED", updatedLoan.getStatus(), "Loan should be CLOSED after full payment");
    }

    @Test
    public void testNoOutstandingInstallments() throws Exception {
        String email = "none_" + UUID.randomUUID().toString() + "@example.com";
        String token = createUserAndGetToken(email);
        Loan loan = createLoanWithSchedules(email, 1, new BigDecimal("100.00"));
        
        // Pay the full amount first
        PaymentRequest request = new PaymentRequest();
        request.setLoanId(loan.getId());
        request.setAmount(new BigDecimal("100.00"));
        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Make another payment
        PaymentRequest request2 = new PaymentRequest();
        request2.setLoanId(loan.getId());
        request2.setAmount(new BigDecimal("50.00"));
        mockMvc.perform(post("/api/v1/payments")
                .header("Authorization", "Bearer " + token)
                .header("Idempotency-Key", UUID.randomUUID().toString())
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isCreated());

        List<Payment> payments = paymentRepository.findByLoanId(loan.getId());
        assertEquals(2, payments.size());
        
        Payment secondPayment = payments.get(1);
        assertEquals(0, new BigDecimal("50.00").compareTo(secondPayment.getUnappliedAmount()));
    }

}

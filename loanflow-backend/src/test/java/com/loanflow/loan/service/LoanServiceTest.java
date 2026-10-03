package com.loanflow.loan.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.dto.LoanResponse;
import com.loanflow.loan.dto.LoanSummaryResponse;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.RepaymentScheduleResponse;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import com.loanflow.repayment.service.EmiCalculatorService;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.Role;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Mock
    private CustomerProfileRepository customerProfileRepository;

    @Mock
    private UserRepository userRepository;

    @Spy
    private EmiCalculatorService emiCalculatorService = new EmiCalculatorService();

    @InjectMocks
    private LoanService loanService;

    private User customerUser;
    private User adminUser;
    private CustomerProfile customerProfile;
    private Loan customerLoan;

    @BeforeEach
    void setUp() {
        Role custRole = new Role();
        custRole.setId(1);
        custRole.setName("CUSTOMER");

        customerUser = new User();
        customerUser.setId(10L);
        customerUser.setEmail("customer@test.com");
        customerUser.setRoles(Set.of(custRole));

        Role adminRole = new Role();
        adminRole.setId(2);
        adminRole.setName("ADMIN");

        adminUser = new User();
        adminUser.setId(20L);
        adminUser.setEmail("admin@test.com");
        adminUser.setRoles(Set.of(adminRole));

        customerProfile = new CustomerProfile();
        customerProfile.setId(100L);
        customerProfile.setUser(customerUser);
        customerProfile.setFirstName("Jane");
        customerProfile.setLastName("Doe");

        LoanProduct product = new LoanProduct();
        product.setId(5L);
        product.setName("Personal Loan");

        com.loanflow.loan.entity.LoanApplication app = new com.loanflow.loan.entity.LoanApplication();
        app.setId(50L);

        customerLoan = new Loan();
        customerLoan.setId(500L);
        customerLoan.setApplication(app);
        customerLoan.setCustomer(customerProfile);
        customerLoan.setProduct(product);
        customerLoan.setPrincipalAmount(new BigDecimal("10000.0000"));
        customerLoan.setInterestRate(new BigDecimal("0.1200"));
        customerLoan.setTermMonths(12);
        customerLoan.setInterestCalculationMethod("FLAT");
        customerLoan.setStatus("DISBURSED");
        customerLoan.setCreatedAt(LocalDateTime.now());

        lenient().when(userRepository.findById(anyLong())).thenAnswer(invocation -> {
            Long id = invocation.getArgument(0);
            if (Long.valueOf(20L).equals(id)) {
                return Optional.of(adminUser);
            }
            return Optional.of(customerUser);
        });
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void authenticateUser(User user) {
        UserDetailsImpl userDetails = new UserDetailsImpl(user);
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(userDetails, null, userDetails.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("getMyLoans: Customer retrieves only owned loans")
    void getMyLoans_customerReturnsOwnedLoans() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByCustomerId(eq(100L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(customerLoan)));

        PaginatedResponse<LoanSummaryResponse> response = loanService.getMyLoans(0, 10);

        assertNotNull(response);
        assertEquals(1, response.getContent().size());
        assertEquals(500L, response.getContent().get(0).getId());
    }

    @Test
    @DisplayName("getLoanById: Customer retrieves own loan successfully")
    void getLoanById_customerOwnerSuccess() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByIdAndCustomerId(500L, 100L)).thenReturn(Optional.of(customerLoan));

        LoanResponse response = loanService.getLoanById(500L);

        assertNotNull(response);
        assertEquals(500L, response.getId());
        assertEquals("Jane Doe", response.getCustomerName());
    }

    @Test
    @DisplayName("getLoanById: Customer accessing unowned loan throws ResourceNotFoundException")
    void getLoanById_customerUnownedThrowsNotFound() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByIdAndCustomerId(999L, 100L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> loanService.getLoanById(999L));
    }

    @Test
    @DisplayName("getLoanById: Admin retrieves any loan by ID")
    void getLoanById_adminSuccess() {
        authenticateUser(adminUser);
        when(loanRepository.findById(500L)).thenReturn(Optional.of(customerLoan));

        LoanResponse response = loanService.getLoanById(500L);

        assertNotNull(response);
        assertEquals(500L, response.getId());
    }

    @Test
    @DisplayName("getLoanSchedule: Returns repayment schedule ordered by installment number")
    void getLoanSchedule_returnsOrderedSchedule() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByIdAndCustomerId(500L, 100L)).thenReturn(Optional.of(customerLoan));

        RepaymentSchedule s1 = new RepaymentSchedule();
        s1.setId(1L);
        s1.setLoan(customerLoan);
        s1.setInstallmentNumber(1);
        s1.setDueDate(LocalDate.of(2026, 2, 15));
        s1.setPrincipalAmount(new BigDecimal("833.3333"));
        s1.setInterestAmount(new BigDecimal("100.0000"));
        s1.setTotalAmount(new BigDecimal("933.3333"));

        RepaymentSchedule s2 = new RepaymentSchedule();
        s2.setId(2L);
        s2.setLoan(customerLoan);
        s2.setInstallmentNumber(2);
        s2.setDueDate(LocalDate.of(2026, 3, 15));
        s2.setPrincipalAmount(new BigDecimal("833.3333"));
        s2.setInterestAmount(new BigDecimal("100.0000"));
        s2.setTotalAmount(new BigDecimal("933.3333"));

        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(500L))
                .thenReturn(List.of(s1, s2));

        List<RepaymentScheduleResponse> schedule = loanService.getLoanSchedule(500L);

        assertNotNull(schedule);
        assertEquals(2, schedule.size());
        assertEquals(1, schedule.get(0).getInstallmentNumber());
        assertEquals(2, schedule.get(1).getInstallmentNumber());
    }

    @Test
    @DisplayName("calculateEmi: Preview calculates correctly and persists nothing")
    void calculateEmi_previewSuccess() {
        EmiCalculationRequest request = new EmiCalculationRequest(
                new BigDecimal("10000.0000"),
                new BigDecimal("0.1200"),
                12,
                "FLAT"
        );

        EmiCalculationResponse response = loanService.calculateEmi(request);

        assertNotNull(response);
        assertEquals(new BigDecimal("10000.0000"), response.getPrincipalAmount());
        assertEquals(new BigDecimal("1200.0000"), response.getTotalInterest());
        assertEquals(new BigDecimal("11200.0000"), response.getTotalRepayment());
        verifyNoInteractions(loanRepository, repaymentScheduleRepository);
    }

    @Test
    @DisplayName("getLoanRepaymentSummary: Returns aggregated summary correctly")
    void getLoanRepaymentSummary_success() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByIdAndCustomerId(500L, 100L)).thenReturn(Optional.of(customerLoan));

        RepaymentSchedule s1 = new RepaymentSchedule();
        s1.setId(1L);
        s1.setLoan(customerLoan);
        s1.setTotalAmount(new BigDecimal("1000.0000"));
        s1.setAmountPaid(new BigDecimal("1000.0000"));
        s1.setStatus("PAID");

        RepaymentSchedule s2 = new RepaymentSchedule();
        s2.setId(2L);
        s2.setLoan(customerLoan);
        s2.setTotalAmount(new BigDecimal("1000.0000"));
        s2.setAmountPaid(new BigDecimal("500.0000"));
        s2.setStatus("PARTIAL");

        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(500L))
                .thenReturn(List.of(s1, s2));

        com.loanflow.repayment.dto.RepaymentSummaryResponse summary = loanService.getLoanRepaymentSummary(500L);

        assertNotNull(summary);
        assertEquals(new BigDecimal("2000.0000"), summary.getTotalScheduledAmount());
        assertEquals(new BigDecimal("1500.0000"), summary.getTotalPaid());
        assertEquals(new BigDecimal("500.0000"), summary.getOutstandingBalance());
        assertEquals(1, summary.getPaidInstallments());
        assertEquals(1, summary.getPendingInstallments());
    }

    @Test
    @DisplayName("getCustomerRepaymentOverview: Returns overview for customer active loans")
    void getCustomerRepaymentOverview_success() {
        authenticateUser(customerUser);
        when(customerProfileRepository.findByUserId(10L)).thenReturn(Optional.of(customerProfile));
        when(loanRepository.findByCustomerId(100L)).thenReturn(List.of(customerLoan));

        RepaymentSchedule s1 = new RepaymentSchedule();
        s1.setId(1L);
        s1.setLoan(customerLoan);
        s1.setTotalAmount(new BigDecimal("1000.0000"));
        s1.setAmountPaid(new BigDecimal("500.0000"));
        s1.setStatus("PARTIAL");

        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(500L))
                .thenReturn(List.of(s1));

        com.loanflow.repayment.dto.CustomerRepaymentOverviewResponse overview = loanService.getCustomerRepaymentOverview();

        assertNotNull(overview);
        assertEquals(1, overview.getTotalActiveLoans());
        assertEquals(new BigDecimal("500.0000"), overview.getTotalOutstandingBalance());
        assertEquals(new BigDecimal("500.0000"), overview.getTotalPaidAmount());
        assertEquals(1, overview.getLoanSummaries().size());
    }
}

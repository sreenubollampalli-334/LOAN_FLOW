package com.loanflow.loan.service;

import com.loanflow.dto.PaginatedResponse;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.dto.LoanResponse;
import com.loanflow.loan.dto.LoanSummaryResponse;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.profile.entity.CustomerProfile;
import com.loanflow.profile.repository.CustomerProfileRepository;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.RepaymentScheduleResponse;
import com.loanflow.repayment.dto.RepaymentSummaryResponse;
import com.loanflow.repayment.dto.CustomerRepaymentOverviewResponse;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import com.loanflow.repayment.service.EmiCalculatorService;
import com.loanflow.security.UserDetailsImpl;
import com.loanflow.user.entity.User;
import com.loanflow.user.repository.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class LoanService {

    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final CustomerProfileRepository customerProfileRepository;
    private final UserRepository userRepository;
    private final EmiCalculatorService emiCalculatorService;

    public LoanService(LoanRepository loanRepository,
                       RepaymentScheduleRepository repaymentScheduleRepository,
                       CustomerProfileRepository customerProfileRepository,
                       UserRepository userRepository,
                       EmiCalculatorService emiCalculatorService) {
        this.loanRepository = loanRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.customerProfileRepository = customerProfileRepository;
        this.userRepository = userRepository;
        this.emiCalculatorService = emiCalculatorService;
    }

    private User getAuthenticatedUser() {
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();
        if (principal instanceof UserDetailsImpl) {
            Long userId = ((UserDetailsImpl) principal).getUser().getId();
            return userRepository.findById(userId)
                    .orElseThrow(() -> new ResourceNotFoundException("User not found"));
        }
        throw new ResourceNotFoundException("User not found in security context");
    }

    private CustomerProfile getAuthenticatedCustomerProfile() {
        User user = getAuthenticatedUser();
        return customerProfileRepository.findByUserId(user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Customer profile not found"));
    }

    private boolean isStaffUser(User user) {
        if (user == null || user.getRoles() == null) return false;
        return user.getRoles().stream().anyMatch(role ->
                "ADMIN".equalsIgnoreCase(role.getName()) ||
                "ROLE_ADMIN".equalsIgnoreCase(role.getName()) ||
                "MANAGER".equalsIgnoreCase(role.getName()) ||
                "ROLE_MANAGER".equalsIgnoreCase(role.getName())
        );
    }

    @Transactional(readOnly = true)
    public PaginatedResponse<LoanSummaryResponse> getMyLoans(int page, int size) {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();

        if (size > 50) size = 50;
        Pageable pageable = PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));

        Page<Loan> loanPage = loanRepository.findByCustomerId(customerProfile.getId(), pageable);

        List<LoanSummaryResponse> content = loanPage.getContent().stream()
                .map(this::mapToSummaryResponse)
                .collect(Collectors.toList());

        return new PaginatedResponse<>(
                content,
                loanPage.getNumber(),
                loanPage.getSize(),
                loanPage.getTotalElements(),
                loanPage.getTotalPages(),
                loanPage.isFirst(),
                loanPage.isLast()
        );
    }

    @Transactional(readOnly = true)
    public LoanResponse getLoanById(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID cannot be null");
        }

        User user = getAuthenticatedUser();
        Loan loan;

        if (isStaffUser(user)) {
            loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        } else {
            CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
            loan = loanRepository.findByIdAndCustomerId(loanId, customerProfile.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        }

        return mapToResponse(loan);
    }

    @Transactional(readOnly = true)
    public List<RepaymentScheduleResponse> getLoanSchedule(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID cannot be null");
        }

        User user = getAuthenticatedUser();
        Loan loan;

        if (isStaffUser(user)) {
            loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        } else {
            CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
            loan = loanRepository.findByIdAndCustomerId(loanId, customerProfile.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        }

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        return schedules.stream()
                .map(this::mapToScheduleResponse)
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public RepaymentSummaryResponse getLoanRepaymentSummary(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID cannot be null");
        }

        User user = getAuthenticatedUser();
        Loan loan;

        if (isStaffUser(user)) {
            loan = loanRepository.findById(loanId)
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        } else {
            CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
            loan = loanRepository.findByIdAndCustomerId(loanId, customerProfile.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));
        }

        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        return calculateSummary(loan.getId(), schedules);
    }

    @Transactional(readOnly = true)
    public CustomerRepaymentOverviewResponse getCustomerRepaymentOverview() {
        CustomerProfile customerProfile = getAuthenticatedCustomerProfile();
        
        List<Loan> activeLoans = loanRepository.findByCustomerId(customerProfile.getId());
        
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        List<RepaymentSummaryResponse> summaries = new java.util.ArrayList<>();
        
        for (Loan loan : activeLoans) {
            List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
            RepaymentSummaryResponse summary = calculateSummary(loan.getId(), schedules);
            summaries.add(summary);
            totalOutstanding = totalOutstanding.add(summary.getOutstandingBalance());
            totalPaid = totalPaid.add(summary.getTotalPaid());
        }
        
        return new CustomerRepaymentOverviewResponse(totalOutstanding, totalPaid, activeLoans.size(), summaries);
    }

    private RepaymentSummaryResponse calculateSummary(Long loanId, List<RepaymentSchedule> schedules) {
        BigDecimal totalScheduledAmount = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalRefunded = BigDecimal.ZERO;
        int pendingInstallments = 0;
        int paidInstallments = 0;

        for (RepaymentSchedule schedule : schedules) {
            totalScheduledAmount = totalScheduledAmount.add(schedule.getTotalAmount() != null ? schedule.getTotalAmount() : BigDecimal.ZERO);
            totalPaid = totalPaid.add(schedule.getAmountPaid() != null ? schedule.getAmountPaid() : BigDecimal.ZERO);
            totalRefunded = totalRefunded.add(schedule.getAmountRefunded() != null ? schedule.getAmountRefunded() : BigDecimal.ZERO);
            
            if ("PAID".equals(schedule.getStatus())) {
                paidInstallments++;
            } else if ("PENDING".equals(schedule.getStatus()) || "PARTIAL".equals(schedule.getStatus())) {
                pendingInstallments++;
            }
        }
        
        BigDecimal outstandingBalance = totalScheduledAmount.subtract(totalPaid).add(totalRefunded);
        
        return new RepaymentSummaryResponse(loanId, totalScheduledAmount, totalPaid, totalRefunded, outstandingBalance, pendingInstallments, paidInstallments);
    }

    public EmiCalculationResponse calculateEmi(EmiCalculationRequest request) {
        return emiCalculatorService.calculate(request);
    }

    private LoanResponse mapToResponse(Loan loan) {
        LoanResponse response = new LoanResponse();
        response.setId(loan.getId());
        response.setApplicationId(loan.getApplication().getId());
        response.setCustomerId(loan.getCustomer().getId());
        response.setCustomerName(loan.getCustomer().getFirstName() + " " + loan.getCustomer().getLastName());
        response.setProductId(loan.getProduct().getId());
        response.setProductName(loan.getProduct().getName());
        response.setPrincipalAmount(loan.getPrincipalAmount());
        response.setInterestRate(loan.getInterestRate());
        response.setInterestCalculationMethod(loan.getInterestCalculationMethod());
        response.setTermMonths(loan.getTermMonths());
        response.setStatus(loan.getStatus());
        response.setCreatedAt(loan.getCreatedAt());
        response.setUpdatedAt(loan.getUpdatedAt());
        return response;
    }

    private LoanSummaryResponse mapToSummaryResponse(Loan loan) {
        LoanSummaryResponse response = new LoanSummaryResponse();
        response.setId(loan.getId());
        response.setApplicationId(loan.getApplication().getId());
        response.setProductId(loan.getProduct().getId());
        response.setProductName(loan.getProduct().getName());
        response.setPrincipalAmount(loan.getPrincipalAmount());
        response.setInterestRate(loan.getInterestRate());
        response.setInterestCalculationMethod(loan.getInterestCalculationMethod());
        response.setTermMonths(loan.getTermMonths());
        response.setStatus(loan.getStatus());
        response.setCreatedAt(loan.getCreatedAt());
        return response;
    }

    private RepaymentScheduleResponse mapToScheduleResponse(RepaymentSchedule schedule) {
        BigDecimal total = schedule.getTotalAmount() != null ? schedule.getTotalAmount() : BigDecimal.ZERO;
        BigDecimal paid = schedule.getAmountPaid() != null ? schedule.getAmountPaid() : BigDecimal.ZERO;
        BigDecimal refunded = schedule.getAmountRefunded() != null ? schedule.getAmountRefunded() : BigDecimal.ZERO;
        BigDecimal netOutstanding = total.subtract(paid).add(refunded);
        
        return new RepaymentScheduleResponse(
                schedule.getId(),
                schedule.getLoan().getId(),
                schedule.getInstallmentNumber(),
                schedule.getDueDate(),
                schedule.getPrincipalAmount(),
                schedule.getInterestAmount(),
                schedule.getTotalAmount(),
                schedule.getAmountPaid(),
                schedule.getAmountRefunded(),
                netOutstanding,
                schedule.getStatus()
        );
    }
}

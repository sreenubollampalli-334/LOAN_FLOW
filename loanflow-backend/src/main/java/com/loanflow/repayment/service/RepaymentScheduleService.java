package com.loanflow.repayment.service;

import com.loanflow.disbursement.entity.Disbursement;
import com.loanflow.disbursement.repository.DisbursementRepository;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.InstallmentBreakdownResponse;
import com.loanflow.repayment.dto.RepaymentScheduleResponse;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class RepaymentScheduleService {

    public static final String INITIAL_SCHEDULE_STATUS = "PENDING";
    public static final int MONETARY_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    private final LoanRepository loanRepository;
    private final RepaymentScheduleRepository repaymentScheduleRepository;
    private final DisbursementRepository disbursementRepository;
    private final EmiCalculatorService emiCalculatorService;

    public RepaymentScheduleService(LoanRepository loanRepository,
                                   RepaymentScheduleRepository repaymentScheduleRepository,
                                   DisbursementRepository disbursementRepository,
                                   EmiCalculatorService emiCalculatorService) {
        this.loanRepository = loanRepository;
        this.repaymentScheduleRepository = repaymentScheduleRepository;
        this.disbursementRepository = disbursementRepository;
        this.emiCalculatorService = emiCalculatorService;
    }

    @Transactional
    public List<RepaymentSchedule> generateAndSaveSchedule(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID cannot be null");
        }

        // Acquire pessimistic write lock on loan row to prevent concurrent duplicate schedule creation
        Loan loan = loanRepository.findByIdForUpdate(loanId)
                .orElseThrow(() -> new ResourceNotFoundException("Loan not found with ID: " + loanId));

        return generateAndSaveSchedule(loan);
    }

    @Transactional
    public List<RepaymentSchedule> generateAndSaveSchedule(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan cannot be null");
        }

        validateLoanStatusForScheduleGeneration(loan);

        // Check for existing schedules (Idempotency check)
        List<RepaymentSchedule> existing = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loan.getId());
        if (!existing.isEmpty()) {
            if (existing.size() == loan.getTermMonths()) {
                return existing; // Return existing schedule if complete
            }
            throw new ResourceConflictException("Incomplete or invalid repayment schedule already exists for loan ID: " + loan.getId());
        }

        LocalDate startDate = determineScheduleStartDate(loan);
        List<RepaymentSchedule> schedules = generateScheduleEntities(loan, startDate);

        // Flush inside transaction to enforce database constraints immediately
        return repaymentScheduleRepository.saveAllAndFlush(schedules);
    }

    public List<RepaymentSchedule> generateScheduleEntities(Loan loan, LocalDate startDate) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan cannot be null");
        }
        if (startDate == null) {
            throw new IllegalArgumentException("Schedule start date cannot be null");
        }

        EmiCalculationResponse calculation = emiCalculatorService.calculateForLoan(loan);
        List<InstallmentBreakdownResponse> breakdownList = calculation.getBreakdown();

        List<RepaymentSchedule> schedules = new ArrayList<>();

        for (InstallmentBreakdownResponse item : breakdownList) {
            int installmentNo = item.getInstallmentNumber();
            // Start date plus N months preserves month-end dates (e.g. Jan 31 -> Feb 28 -> Mar 31) without drift
            LocalDate dueDate = startDate.plusMonths(installmentNo);

            RepaymentSchedule schedule = new RepaymentSchedule();
            schedule.setLoan(loan);
            schedule.setInstallmentNumber(installmentNo);
            schedule.setDueDate(dueDate);
            schedule.setPrincipalAmount(item.getPrincipalAmount().setScale(MONETARY_SCALE, DEFAULT_ROUNDING));
            schedule.setInterestAmount(item.getInterestAmount().setScale(MONETARY_SCALE, DEFAULT_ROUNDING));
            schedule.setTotalAmount(item.getTotalAmount().setScale(MONETARY_SCALE, DEFAULT_ROUNDING));
            schedule.setAmountPaid(BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING));
            schedule.setAmountRefunded(BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING));
            schedule.setStatus(INITIAL_SCHEDULE_STATUS);

            schedules.add(schedule);
        }

        return schedules;
    }

    @Transactional(readOnly = true)
    public List<RepaymentScheduleResponse> getScheduleForLoan(Long loanId) {
        if (loanId == null) {
            throw new IllegalArgumentException("Loan ID cannot be null");
        }
        List<RepaymentSchedule> schedules = repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(loanId);
        return schedules.stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    public LocalDate determineScheduleStartDate(Loan loan) {
        if (loan.getId() != null) {
            Optional<Disbursement> disbOpt = disbursementRepository.findByLoanId(loan.getId());
            if (disbOpt.isPresent() && disbOpt.get().getDisbursedAt() != null) {
                return disbOpt.get().getDisbursedAt().toLocalDate();
            }
        }
        if (loan.getCreatedAt() != null) {
            return loan.getCreatedAt().toLocalDate();
        }
        return LocalDate.now();
    }

    private void validateLoanStatusForScheduleGeneration(Loan loan) {
        String status = loan.getStatus();
        if (!"APPROVED".equalsIgnoreCase(status) && !"DISBURSED".equalsIgnoreCase(status)) {
            throw new ResourceConflictException("Loan status does not permit schedule generation: " + status);
        }
    }

    private RepaymentScheduleResponse mapToResponse(RepaymentSchedule schedule) {
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

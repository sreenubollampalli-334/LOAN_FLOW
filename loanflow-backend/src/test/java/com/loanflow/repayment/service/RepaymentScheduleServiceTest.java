package com.loanflow.repayment.service;

import com.loanflow.disbursement.entity.Disbursement;
import com.loanflow.disbursement.repository.DisbursementRepository;
import com.loanflow.exception.ResourceConflictException;
import com.loanflow.exception.ResourceNotFoundException;
import com.loanflow.loan.entity.Loan;
import com.loanflow.loan.repository.LoanRepository;
import com.loanflow.repayment.entity.RepaymentSchedule;
import com.loanflow.repayment.repository.RepaymentScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RepaymentScheduleServiceTest {

    @Mock
    private LoanRepository loanRepository;

    @Mock
    private RepaymentScheduleRepository repaymentScheduleRepository;

    @Mock
    private DisbursementRepository disbursementRepository;

    @Spy
    private EmiCalculatorService emiCalculatorService = new EmiCalculatorService();

    @InjectMocks
    private RepaymentScheduleService scheduleService;

    private Loan sampleLoan;

    @BeforeEach
    void setUp() {
        sampleLoan = new Loan();
        sampleLoan.setId(100L);
        sampleLoan.setPrincipalAmount(new BigDecimal("12000.0000"));
        sampleLoan.setInterestRate(new BigDecimal("0.1200"));
        sampleLoan.setTermMonths(12);
        sampleLoan.setInterestCalculationMethod("FLAT");
        sampleLoan.setStatus("APPROVED");
        sampleLoan.setCreatedAt(LocalDateTime.of(2026, 1, 15, 10, 0));
    }

    @Test
    @DisplayName("1. FLAT schedule totals and installment breakdown: Validates sum of principal and interest match exactly")
    void testFlatScheduleTotalsAndBreakdown() {
        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(Collections.emptyList());
        when(repaymentScheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RepaymentSchedule> schedules = scheduleService.generateAndSaveSchedule(100L);

        assertNotNull(schedules);
        assertEquals(12, schedules.size());

        BigDecimal sumPrincipal = BigDecimal.ZERO;
        BigDecimal sumInterest = BigDecimal.ZERO;

        for (RepaymentSchedule rs : schedules) {
            assertEquals(100L, rs.getLoan().getId());
            assertEquals(new BigDecimal("0.0000"), rs.getAmountPaid());
            assertEquals(new BigDecimal("0.0000"), rs.getAmountRefunded());
            assertEquals("PENDING", rs.getStatus());
            assertEquals(rs.getPrincipalAmount().add(rs.getInterestAmount()), rs.getTotalAmount());

            sumPrincipal = sumPrincipal.add(rs.getPrincipalAmount());
            sumInterest = sumInterest.add(rs.getInterestAmount());
        }

        assertEquals(new BigDecimal("12000.0000"), sumPrincipal);
        assertEquals(new BigDecimal("1440.0000"), sumInterest);
    }

    @Test
    @DisplayName("2. REDUCING_BALANCE amortization and final principal adjustment: Ensures final installment zeroes remaining principal")
    void testReducingBalanceAmortizationAndFinalAdjustment() {
        sampleLoan.setInterestCalculationMethod("REDUCING_BALANCE");
        sampleLoan.setPrincipalAmount(new BigDecimal("10000.0000"));
        sampleLoan.setInterestRate(new BigDecimal("0.1200"));
        sampleLoan.setTermMonths(12);

        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(Collections.emptyList());
        when(repaymentScheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RepaymentSchedule> schedules = scheduleService.generateAndSaveSchedule(100L);

        assertNotNull(schedules);
        assertEquals(12, schedules.size());

        BigDecimal sumPrincipal = BigDecimal.ZERO;
        for (RepaymentSchedule rs : schedules) {
            assertTrue(rs.getPrincipalAmount().compareTo(BigDecimal.ZERO) > 0);
            sumPrincipal = sumPrincipal.add(rs.getPrincipalAmount());
        }

        // Sum of all principal portions must equal loan principal exactly
        assertEquals(new BigDecimal("10000.0000"), sumPrincipal);
    }

    @Test
    @DisplayName("3. Zero-interest schedule: Generates schedule with zero interest for 0% loan rate")
    void testZeroInterestSchedule() {
        sampleLoan.setInterestRate(BigDecimal.ZERO);

        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(Collections.emptyList());
        when(repaymentScheduleRepository.saveAllAndFlush(anyList())).thenAnswer(invocation -> invocation.getArgument(0));

        List<RepaymentSchedule> schedules = scheduleService.generateAndSaveSchedule(100L);

        assertEquals(12, schedules.size());
        BigDecimal sumPrincipal = BigDecimal.ZERO;

        for (RepaymentSchedule rs : schedules) {
            assertEquals(new BigDecimal("0.0000"), rs.getInterestAmount());
            assertEquals(rs.getPrincipalAmount(), rs.getTotalAmount());
            sumPrincipal = sumPrincipal.add(rs.getPrincipalAmount());
        }

        assertEquals(new BigDecimal("12000.0000"), sumPrincipal);
    }

    @Test
    @DisplayName("4. Correct installment count and due dates: Uses authoritative disbursement date when available")
    void testCorrectInstallmentCountAndDueDates() {
        sampleLoan.setTermMonths(6);

        Disbursement disbursement = new Disbursement();
        disbursement.setDisbursedAt(LocalDateTime.of(2026, 3, 20, 14, 30));

        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(disbursementRepository.findByLoanId(100L)).thenReturn(Optional.of(disbursement));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(Collections.emptyList());
        when(repaymentScheduleRepository.saveAllAndFlush(anyList())).thenAnswer(i -> i.getArgument(0));

        List<RepaymentSchedule> schedules = scheduleService.generateAndSaveSchedule(100L);

        assertEquals(6, schedules.size());
        assertEquals(LocalDate.of(2026, 4, 20), schedules.get(0).getDueDate());
        assertEquals(LocalDate.of(2026, 5, 20), schedules.get(1).getDueDate());
        assertEquals(LocalDate.of(2026, 6, 20), schedules.get(2).getDueDate());
        assertEquals(LocalDate.of(2026, 7, 20), schedules.get(3).getDueDate());
        assertEquals(LocalDate.of(2026, 8, 20), schedules.get(4).getDueDate());
        assertEquals(LocalDate.of(2026, 9, 20), schedules.get(5).getDueDate());
    }

    @Test
    @DisplayName("5. Month-end date behavior: Preserves month-end day without date drift across months")
    void testMonthEndDateBehavior() {
        sampleLoan.setTermMonths(4);
        sampleLoan.setCreatedAt(LocalDateTime.of(2026, 1, 31, 12, 0));

        List<RepaymentSchedule> schedules = scheduleService.generateScheduleEntities(sampleLoan, LocalDate.of(2026, 1, 31));

        assertEquals(4, schedules.size());
        assertEquals(LocalDate.of(2026, 2, 28), schedules.get(0).getDueDate()); // Feb end
        assertEquals(LocalDate.of(2026, 3, 31), schedules.get(1).getDueDate()); // Mar end (31st preserved!)
        assertEquals(LocalDate.of(2026, 4, 30), schedules.get(2).getDueDate()); // Apr end
        assertEquals(LocalDate.of(2026, 5, 31), schedules.get(3).getDueDate()); // May end
    }

    @Test
    @DisplayName("6. Invalid loan status: Throws ResourceConflictException when loan is not APPROVED or DISBURSED")
    void testInvalidLoanStatus() {
        sampleLoan.setStatus("DRAFT");
        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));

        assertThrows(ResourceConflictException.class, () -> scheduleService.generateAndSaveSchedule(100L));

        sampleLoan.setStatus("SUBMITTED");
        assertThrows(ResourceConflictException.class, () -> scheduleService.generateAndSaveSchedule(100L));

        sampleLoan.setStatus("REJECTED");
        assertThrows(ResourceConflictException.class, () -> scheduleService.generateAndSaveSchedule(100L));

        verify(repaymentScheduleRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("7. Duplicate generation/idempotency: Returns existing schedule without re-inserting")
    void testDuplicateGenerationIdempotency() {
        RepaymentSchedule existingItem = new RepaymentSchedule();
        existingItem.setId(1L);
        existingItem.setLoan(sampleLoan);
        existingItem.setInstallmentNumber(1);

        List<RepaymentSchedule> existingList = new ArrayList<>();
        for (int i = 1; i <= 12; i++) {
            RepaymentSchedule rs = new RepaymentSchedule();
            rs.setId((long) i);
            rs.setLoan(sampleLoan);
            rs.setInstallmentNumber(i);
            existingList.add(rs);
        }

        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(existingList);

        List<RepaymentSchedule> result = scheduleService.generateAndSaveSchedule(100L);

        assertEquals(12, result.size());
        assertEquals(1L, result.get(0).getId());
        verify(repaymentScheduleRepository, never()).saveAllAndFlush(anyList());
    }

    @Test
    @DisplayName("8. Rollback if persistence fails: Propagates exception and creates no partial schedules")
    void testRollbackIfPersistenceFails() {
        when(loanRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(sampleLoan));
        when(repaymentScheduleRepository.findByLoanIdOrderByInstallmentNumberAsc(100L)).thenReturn(Collections.emptyList());
        when(repaymentScheduleRepository.saveAllAndFlush(anyList()))
                .thenThrow(new DataIntegrityViolationException("Unique constraint violation on unq_rs_loan_installment"));

        assertThrows(DataIntegrityViolationException.class, () -> scheduleService.generateAndSaveSchedule(100L));
    }

    @Test
    @DisplayName("Null input validation: Throws IllegalArgumentException for null arguments")
    void testNullInputValidation() {
        assertThrows(IllegalArgumentException.class, () -> scheduleService.generateAndSaveSchedule((Long) null));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.generateAndSaveSchedule((Loan) null));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.generateScheduleEntities(null, LocalDate.now()));
        assertThrows(IllegalArgumentException.class, () -> scheduleService.generateScheduleEntities(sampleLoan, null));
    }
}

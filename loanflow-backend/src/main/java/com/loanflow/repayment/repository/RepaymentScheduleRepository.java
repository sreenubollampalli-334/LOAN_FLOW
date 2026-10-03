package com.loanflow.repayment.repository;

import com.loanflow.repayment.entity.RepaymentSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.util.Optional;

@Repository
public interface RepaymentScheduleRepository extends JpaRepository<RepaymentSchedule, Long> {
    List<RepaymentSchedule> findByLoanIdOrderByInstallmentNumberAsc(Long loanId);
    List<RepaymentSchedule> findByLoanIdAndStatus(Long loanId, String status);
    Optional<RepaymentSchedule> findByLoanIdAndInstallmentNumber(Long loanId, Integer installmentNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rs FROM RepaymentSchedule rs WHERE rs.loan.id = :loanId AND rs.status != 'PAID' ORDER BY rs.id ASC")
    List<RepaymentSchedule> findPendingSchedulesForUpdateByLoanId(@Param("loanId") Long loanId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT rs FROM RepaymentSchedule rs WHERE rs.id IN :ids ORDER BY rs.id ASC")
    List<RepaymentSchedule> findByIdInWithLock(@Param("ids") java.util.List<Long> ids);
}

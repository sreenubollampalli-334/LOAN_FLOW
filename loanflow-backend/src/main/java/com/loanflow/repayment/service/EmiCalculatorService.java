package com.loanflow.repayment.service;

import com.loanflow.loan.entity.Loan;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.InstallmentBreakdownResponse;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

@Service
public class EmiCalculatorService {

    public static final String METHOD_FLAT = "FLAT";
    public static final String METHOD_REDUCING_BALANCE = "REDUCING_BALANCE";
    public static final String METHOD_REDUCING_ALIAS = "REDUCING";
    public static final int MONETARY_SCALE = 4;
    public static final RoundingMode DEFAULT_ROUNDING = RoundingMode.HALF_UP;

    public EmiCalculationResponse calculate(EmiCalculationRequest request) {
        if (request == null) {
            throw new IllegalArgumentException("EMI calculation request cannot be null");
        }
        return calculate(
                request.getPrincipalAmount(),
                request.getInterestRate(),
                request.getTermMonths(),
                request.getInterestCalculationMethod()
        );
    }

    public EmiCalculationResponse calculateForLoan(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan cannot be null");
        }
        return calculate(
                loan.getPrincipalAmount(),
                loan.getInterestRate(),
                loan.getTermMonths(),
                loan.getInterestCalculationMethod()
        );
    }

    public EmiCalculationResponse calculateForProduct(LoanProduct product, BigDecimal principalAmount, Integer termMonths, String interestCalculationMethod) {
        if (product == null) {
            throw new IllegalArgumentException("Loan product cannot be null");
        }
        return calculate(
                principalAmount,
                product.getInterestRate(),
                termMonths,
                interestCalculationMethod
        );
    }

    public EmiCalculationResponse calculate(BigDecimal principalAmount, BigDecimal interestRate, Integer termMonths, String interestCalculationMethod) {
        validateInputs(principalAmount, interestRate, termMonths, interestCalculationMethod);

        String normalizedMethod = normalizeMethod(interestCalculationMethod);
        BigDecimal p = principalAmount.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
        BigDecimal rAnnual = interestRate.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
        int n = termMonths;

        if (rAnnual.compareTo(BigDecimal.ZERO) == 0) {
            return calculateZeroInterest(p, n, normalizedMethod);
        }

        if (METHOD_FLAT.equals(normalizedMethod)) {
            return calculateFlatInterest(p, rAnnual, n);
        } else {
            return calculateReducingBalanceInterest(p, rAnnual, n);
        }
    }

    private void validateInputs(BigDecimal principalAmount, BigDecimal interestRate, Integer termMonths, String interestCalculationMethod) {
        if (principalAmount == null || principalAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be greater than zero");
        }
        if (interestRate == null || interestRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Interest rate cannot be negative");
        }
        if (termMonths == null || termMonths < 1) {
            throw new IllegalArgumentException("Term in months must be at least 1");
        }
        String normalized = normalizeMethod(interestCalculationMethod);
        if (!METHOD_FLAT.equals(normalized) && !METHOD_REDUCING_BALANCE.equals(normalized)) {
            throw new IllegalArgumentException("Unsupported interest calculation method: " + interestCalculationMethod);
        }
    }

    private String normalizeMethod(String method) {
        if (method == null || method.trim().isEmpty()) {
            return METHOD_FLAT;
        }
        String trimmed = method.trim().toUpperCase();
        if (METHOD_REDUCING_ALIAS.equals(trimmed)) {
            return METHOD_REDUCING_BALANCE;
        }
        return trimmed;
    }

    private EmiCalculationResponse calculateZeroInterest(BigDecimal p, int n, String method) {
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
        BigDecimal totalRepayment = p;
        BigDecimal monthlyInstallment = p.divide(BigDecimal.valueOf(n), MONETARY_SCALE, DEFAULT_ROUNDING);

        List<InstallmentBreakdownResponse> breakdown = new ArrayList<>();
        BigDecimal remaining = p;
        BigDecimal accumulatedPrincipal = BigDecimal.ZERO;

        for (int i = 1; i <= n; i++) {
            BigDecimal principalPortion;
            if (i == n) {
                principalPortion = remaining;
            } else {
                principalPortion = monthlyInstallment;
            }
            BigDecimal interestPortion = BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            BigDecimal totalAmount = principalPortion;
            accumulatedPrincipal = accumulatedPrincipal.add(principalPortion);
            remaining = p.subtract(accumulatedPrincipal).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            if (remaining.compareTo(BigDecimal.ZERO) < 0 || i == n) {
                remaining = BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            }

            breakdown.add(new InstallmentBreakdownResponse(i, principalPortion, interestPortion, totalAmount, remaining));
        }

        return new EmiCalculationResponse(p, monthlyInstallment, totalInterest, totalRepayment, n, method, breakdown);
    }

    private EmiCalculationResponse calculateFlatInterest(BigDecimal p, BigDecimal rAnnual, int n) {
        // Flat interest: Total Interest = Principal * Annual Rate * (Term Months / 12)
        BigDecimal totalInterest = p.multiply(rAnnual)
                .multiply(BigDecimal.valueOf(n))
                .divide(BigDecimal.valueOf(12), MONETARY_SCALE, DEFAULT_ROUNDING);

        BigDecimal totalRepayment = p.add(totalInterest).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
        BigDecimal monthlyInstallment = totalRepayment.divide(BigDecimal.valueOf(n), MONETARY_SCALE, DEFAULT_ROUNDING);

        List<InstallmentBreakdownResponse> breakdown = new ArrayList<>();
        BigDecimal remainingPrincipal = p;
        BigDecimal accumulatedPrincipal = BigDecimal.ZERO;
        BigDecimal accumulatedInterest = BigDecimal.ZERO;

        BigDecimal baseMonthlyPrincipal = p.divide(BigDecimal.valueOf(n), MONETARY_SCALE, DEFAULT_ROUNDING);
        BigDecimal baseMonthlyInterest = totalInterest.divide(BigDecimal.valueOf(n), MONETARY_SCALE, DEFAULT_ROUNDING);

        for (int i = 1; i <= n; i++) {
            BigDecimal principalPortion;
            BigDecimal interestPortion;

            if (i == n) {
                principalPortion = p.subtract(accumulatedPrincipal).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
                interestPortion = totalInterest.subtract(accumulatedInterest).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            } else {
                principalPortion = baseMonthlyPrincipal;
                interestPortion = baseMonthlyInterest;
            }

            BigDecimal totalAmount = principalPortion.add(interestPortion);
            accumulatedPrincipal = accumulatedPrincipal.add(principalPortion);
            accumulatedInterest = accumulatedInterest.add(interestPortion);

            remainingPrincipal = p.subtract(accumulatedPrincipal).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            if (remainingPrincipal.compareTo(BigDecimal.ZERO) < 0 || i == n) {
                remainingPrincipal = BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            }

            breakdown.add(new InstallmentBreakdownResponse(i, principalPortion, interestPortion, totalAmount, remainingPrincipal));
        }

        return new EmiCalculationResponse(p, monthlyInstallment, totalInterest, totalRepayment, n, METHOD_FLAT, breakdown);
    }

    private EmiCalculationResponse calculateReducingBalanceInterest(BigDecimal p, BigDecimal rAnnual, int n) {
        MathContext mc = MathContext.DECIMAL128;
        BigDecimal rMonthly = rAnnual.divide(BigDecimal.valueOf(12), mc);

        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        BigDecimal onePlusR = BigDecimal.ONE.add(rMonthly, mc);
        BigDecimal pow = onePlusR.pow(n, mc);
        BigDecimal numerator = p.multiply(rMonthly, mc).multiply(pow, mc);
        BigDecimal denominator = pow.subtract(BigDecimal.ONE, mc);

        BigDecimal exactEmi = numerator.divide(denominator, mc);
        BigDecimal monthlyInstallment = exactEmi.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);

        List<InstallmentBreakdownResponse> breakdown = new ArrayList<>();
        BigDecimal currentBalance = p;
        BigDecimal accumulatedInterest = BigDecimal.ZERO;

        for (int i = 1; i <= n; i++) {
            BigDecimal interestPortion = currentBalance.multiply(rMonthly, mc).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            BigDecimal principalPortion;

            if (i == n) {
                principalPortion = currentBalance;
            } else {
                principalPortion = monthlyInstallment.subtract(interestPortion);
                if (principalPortion.compareTo(currentBalance) > 0) {
                    principalPortion = currentBalance;
                }
            }

            BigDecimal totalAmount = principalPortion.add(interestPortion);
            currentBalance = currentBalance.subtract(principalPortion).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            if (currentBalance.compareTo(BigDecimal.ZERO) < 0 || i == n) {
                currentBalance = BigDecimal.ZERO.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
            }

            accumulatedInterest = accumulatedInterest.add(interestPortion);
            breakdown.add(new InstallmentBreakdownResponse(i, principalPortion, interestPortion, totalAmount, currentBalance));
        }

        BigDecimal totalInterest = accumulatedInterest.setScale(MONETARY_SCALE, DEFAULT_ROUNDING);
        BigDecimal totalRepayment = p.add(totalInterest).setScale(MONETARY_SCALE, DEFAULT_ROUNDING);

        return new EmiCalculationResponse(p, monthlyInstallment, totalInterest, totalRepayment, n, METHOD_REDUCING_BALANCE, breakdown);
    }
}

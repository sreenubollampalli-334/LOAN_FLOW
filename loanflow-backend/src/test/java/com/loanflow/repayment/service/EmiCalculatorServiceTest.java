package com.loanflow.repayment.service;

import com.loanflow.loan.entity.Loan;
import com.loanflow.product.entity.LoanProduct;
import com.loanflow.repayment.dto.EmiCalculationRequest;
import com.loanflow.repayment.dto.EmiCalculationResponse;
import com.loanflow.repayment.dto.InstallmentBreakdownResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.*;

class EmiCalculatorServiceTest {

    private EmiCalculatorService emiCalculatorService;

    @BeforeEach
    void setUp() {
        emiCalculatorService = new EmiCalculatorService();
    }

    @Test
    @DisplayName("1. Flat interest calculation: Correctly computes total interest, monthly installment, and total repayment")
    void testFlatInterestCalculation() {
        BigDecimal principal = new BigDecimal("10000.0000");
        BigDecimal rate = new BigDecimal("0.1200");
        int tenure = 12;

        EmiCalculationResponse response = emiCalculatorService.calculate(principal, rate, tenure, "FLAT");

        assertNotNull(response);
        assertEquals(new BigDecimal("10000.0000"), response.getPrincipalAmount());
        assertEquals(new BigDecimal("1200.0000"), response.getTotalInterest());
        assertEquals(new BigDecimal("11200.0000"), response.getTotalRepayment());
        assertEquals(new BigDecimal("933.3333"), response.getMonthlyInstallment());
        assertEquals("FLAT", response.getInterestCalculationMethod());
        assertEquals(12, response.getTermMonths());
        assertEquals(12, response.getBreakdown().size());
    }

    @Test
    @DisplayName("2. Reducing-balance calculation: Correctly computes reducing balance EMI and amortization breakdown")
    void testReducingBalanceCalculation() {
        BigDecimal principal = new BigDecimal("10000.0000");
        BigDecimal rate = new BigDecimal("0.1200");
        int tenure = 12;

        EmiCalculationResponse response = emiCalculatorService.calculate(principal, rate, tenure, "REDUCING_BALANCE");

        assertNotNull(response);
        assertEquals(new BigDecimal("10000.0000"), response.getPrincipalAmount());
        assertEquals(new BigDecimal("888.4879"), response.getMonthlyInstallment());
        assertEquals(new BigDecimal("661.8545"), response.getTotalInterest());
        assertEquals(new BigDecimal("10661.8545"), response.getTotalRepayment());
        assertEquals("REDUCING_BALANCE", response.getInterestCalculationMethod());
        assertEquals(12, response.getBreakdown().size());

        InstallmentBreakdownResponse lastInstallment = response.getBreakdown().get(11);
        assertEquals(12, lastInstallment.getInstallmentNumber());
        assertEquals(new BigDecimal("0.0000"), lastInstallment.getRemainingBalance());
    }

    @Test
    @DisplayName("3. Zero-interest calculation: Correctly handles 0% interest rate without division by zero")
    void testZeroInterestCalculation() {
        BigDecimal principal = new BigDecimal("12000.0000");
        BigDecimal rate = BigDecimal.ZERO;
        int tenure = 12;

        EmiCalculationResponse response = emiCalculatorService.calculate(principal, rate, tenure, "FLAT");

        assertNotNull(response);
        assertEquals(new BigDecimal("12000.0000"), response.getPrincipalAmount());
        assertEquals(new BigDecimal("0.0000"), response.getTotalInterest());
        assertEquals(new BigDecimal("12000.0000"), response.getTotalRepayment());
        assertEquals(new BigDecimal("1000.0000"), response.getMonthlyInstallment());

        for (InstallmentBreakdownResponse item : response.getBreakdown()) {
            assertEquals(new BigDecimal("0.0000"), item.getInterestAmount());
        }
    }

    @Test
    @DisplayName("4. Principal and interest rounding: Applies Half-Up rounding to 4 decimal places consistently")
    void testRoundingPolicy() {
        BigDecimal principal = new BigDecimal("1000.55555");
        BigDecimal rate = new BigDecimal("0.07555");
        int tenure = 7;

        EmiCalculationResponse response = emiCalculatorService.calculate(principal, rate, tenure, "FLAT");

        assertEquals(4, response.getPrincipalAmount().scale());
        assertEquals(4, response.getMonthlyInstallment().scale());
        assertEquals(4, response.getTotalInterest().scale());
        assertEquals(4, response.getTotalRepayment().scale());

        for (InstallmentBreakdownResponse item : response.getBreakdown()) {
            assertEquals(4, item.getPrincipalAmount().scale());
            assertEquals(4, item.getInterestAmount().scale());
            assertEquals(4, item.getTotalAmount().scale());
            assertEquals(4, item.getRemainingBalance().scale());
        }
    }

    @Test
    @DisplayName("5. One-month tenure: Correctly calculates single installment loan")
    void testOneMonthTenure() {
        BigDecimal principal = new BigDecimal("5000.0000");
        BigDecimal rate = new BigDecimal("0.1200");
        int tenure = 1;

        EmiCalculationResponse flatResponse = emiCalculatorService.calculate(principal, rate, tenure, "FLAT");
        assertEquals(1, flatResponse.getBreakdown().size());
        assertEquals(new BigDecimal("5000.0000"), flatResponse.getPrincipalAmount());
        assertEquals(new BigDecimal("50.0000"), flatResponse.getTotalInterest());
        assertEquals(new BigDecimal("5050.0000"), flatResponse.getTotalRepayment());
        assertEquals(new BigDecimal("5050.0000"), flatResponse.getMonthlyInstallment());

        EmiCalculationResponse reducingResponse = emiCalculatorService.calculate(principal, rate, tenure, "REDUCING_BALANCE");
        assertEquals(1, reducingResponse.getBreakdown().size());
        assertEquals(new BigDecimal("5050.0000"), reducingResponse.getTotalRepayment());
    }

    @Test
    @DisplayName("6. Invalid inputs: Throws IllegalArgumentException for invalid principal, rate, or tenure")
    void testInvalidInputs() {
        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(null, new BigDecimal("0.10"), 12, "FLAT"));
        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(BigDecimal.ZERO, new BigDecimal("0.10"), 12, "FLAT"));
        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(new BigDecimal("-100"), new BigDecimal("0.10"), 12, "FLAT"));

        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(new BigDecimal("1000"), new BigDecimal("-0.01"), 12, "FLAT"));

        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(new BigDecimal("1000"), new BigDecimal("0.10"), 0, "FLAT"));
        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(new BigDecimal("1000"), new BigDecimal("0.10"), -5, "FLAT"));

        assertThrows(IllegalArgumentException.class, () ->
                emiCalculatorService.calculate(new BigDecimal("1000"), new BigDecimal("0.10"), 12, "INVALID_METHOD"));
    }

    @Test
    @DisplayName("7. Consistency rule: Verifies totalRepayment = principal + totalInterest for both methods")
    void testTotalRepaymentConsistency() {
        BigDecimal principal = new BigDecimal("25000.5000");
        BigDecimal rate = new BigDecimal("0.1450");
        int tenure = 36;

        EmiCalculationResponse flat = emiCalculatorService.calculate(principal, rate, tenure, "FLAT");
        assertEquals(flat.getPrincipalAmount().add(flat.getTotalInterest()), flat.getTotalRepayment());

        BigDecimal flatBreakdownSum = flat.getBreakdown().stream()
                .map(InstallmentBreakdownResponse::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(flat.getTotalRepayment(), flatBreakdownSum);

        EmiCalculationResponse reducing = emiCalculatorService.calculate(principal, rate, tenure, "REDUCING_BALANCE");
        assertEquals(reducing.getPrincipalAmount().add(reducing.getTotalInterest()), reducing.getTotalRepayment());

        BigDecimal reducingBreakdownSum = reducing.getBreakdown().stream()
                .map(InstallmentBreakdownResponse::getTotalAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertEquals(reducing.getTotalRepayment(), reducingBreakdownSum);
    }

    @Test
    @DisplayName("8. Values compatible with four decimal places (DECIMAL(19,4))")
    void testFourDecimalPlacesCompatibility() {
        EmiCalculationRequest request = new EmiCalculationRequest(
                new BigDecimal("15000.1234"),
                new BigDecimal("0.0875"),
                18,
                "FLAT"
        );

        EmiCalculationResponse response = emiCalculatorService.calculate(request);

        assertEquals(4, response.getPrincipalAmount().scale());
        assertEquals(4, response.getMonthlyInstallment().scale());
        assertEquals(4, response.getTotalInterest().scale());
        assertEquals(4, response.getTotalRepayment().scale());
    }

    @Test
    @DisplayName("Loan and LoanProduct overload methods calculate correctly")
    void testEntityOverloadMethods() {
        LoanProduct product = new LoanProduct();
        product.setInterestRate(new BigDecimal("0.1000"));

        EmiCalculationResponse productResp = emiCalculatorService.calculateForProduct(
                product, new BigDecimal("8000.0000"), 24, "FLAT");
        assertNotNull(productResp);
        assertEquals(new BigDecimal("8000.0000"), productResp.getPrincipalAmount());

        Loan loan = new Loan();
        loan.setPrincipalAmount(new BigDecimal("6000.0000"));
        loan.setInterestRate(new BigDecimal("0.1200"));
        loan.setTermMonths(6);
        loan.setInterestCalculationMethod("FLAT");

        EmiCalculationResponse loanResp = emiCalculatorService.calculateForLoan(loan);
        assertNotNull(loanResp);
        assertEquals(new BigDecimal("6000.0000"), loanResp.getPrincipalAmount());
    }
}

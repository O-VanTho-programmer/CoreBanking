package com.cbs.lending.service;

import com.cbs.lending.dto.AmortizationResult;
import com.cbs.lending.dto.AmortizationScheduleItem;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class AmortizationScheduleServiceTest {

    private AmortizationScheduleService service;
    private final LocalDate startDate = LocalDate.of(2026, 11, 15);

    @BeforeEach
    void setUp() {
        service = new AmortizationScheduleService();
    }

    @Test
    @DisplayName("Equal Principal: 120M VND at 12% per year for 12 months")
    void testEqualPrincipalStandardSchedule() {
        BigDecimal principal = new BigDecimal("120000000.0000");
        BigDecimal annualRate = new BigDecimal("0.1200"); // 12%
        int termMonths = 12;

        AmortizationResult result = service.calculateSchedule(
                principal, annualRate, termMonths, "EQUAL_PRINCIPAL", startDate
        );

        assertThat(result.repaymentType()).isEqualTo("EQUAL_PRINCIPAL");
        assertThat(result.items()).hasSize(12);
        assertThat(result.totalPrincipal()).isEqualByComparingTo(principal);

        // Period 1: Beginning 120M, Principal 10M, Interest 120M * 0.12 / 12 = 1.2M, Total 11.2M
        AmortizationScheduleItem p1 = result.items().get(0);
        assertThat(p1.periodNumber()).isEqualTo(1);
        assertThat(p1.dueDate()).isEqualTo(startDate);
        assertThat(p1.beginningBalance()).isEqualByComparingTo("120000000.0000");
        assertThat(p1.principalDue()).isEqualByComparingTo("10000000.0000");
        assertThat(p1.interestDue()).isEqualByComparingTo("1200000.0000");
        assertThat(p1.totalInstallment()).isEqualByComparingTo("11200000.0000");
        assertThat(p1.endingBalance()).isEqualByComparingTo("110000000.0000");

        // Period 12: Beginning 10M, Principal 10M, Interest 10M * 0.12 / 12 = 100K, Total 10.1M, Ending 0
        AmortizationScheduleItem p12 = result.items().get(11);
        assertThat(p12.periodNumber()).isEqualTo(12);
        assertThat(p12.dueDate()).isEqualTo(startDate.plusMonths(11));
        assertThat(p12.beginningBalance()).isEqualByComparingTo("10000000.0000");
        assertThat(p12.principalDue()).isEqualByComparingTo("10000000.0000");
        assertThat(p12.interestDue()).isEqualByComparingTo("100000.0000");
        assertThat(p12.totalInstallment()).isEqualByComparingTo("10100000.0000");
        assertThat(p12.endingBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Equal Principal: Absorbs fractional cents rounding residual on final period")
    void testEqualPrincipalResidualAbsorption() {
        BigDecimal principal = new BigDecimal("10000000.0000"); // 10M / 3 = 3,333,333.3333
        BigDecimal annualRate = new BigDecimal("0.0950");
        int termMonths = 3;

        AmortizationResult result = service.calculateSchedule(
                principal, annualRate, termMonths, "EQUAL_PRINCIPAL", startDate
        );

        assertThat(result.items()).hasSize(3);
        assertThat(result.totalPrincipal()).isEqualByComparingTo(principal);

        AmortizationScheduleItem p1 = result.items().get(0);
        assertThat(p1.principalDue()).isEqualByComparingTo("3333333.3333");

        AmortizationScheduleItem p2 = result.items().get(1);
        assertThat(p2.principalDue()).isEqualByComparingTo("3333333.3333");

        // Period 3 absorbs remaining 3,333,333.3334
        AmortizationScheduleItem p3 = result.items().get(2);
        assertThat(p3.principalDue()).isEqualByComparingTo("3333333.3334");
        assertThat(p3.endingBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Annuity EMI: Constant monthly installment with zero ending balance")
    void testAnnuityEmiStandardSchedule() {
        BigDecimal principal = new BigDecimal("100000000.0000");
        BigDecimal annualRate = new BigDecimal("0.1200"); // 12%
        int termMonths = 12;

        AmortizationResult result = service.calculateSchedule(
                principal, annualRate, termMonths, "ANNUITY_EMI", startDate
        );

        assertThat(result.repaymentType()).isEqualTo("ANNUITY_EMI");
        assertThat(result.items()).hasSize(12);
        assertThat(result.totalPrincipal()).isEqualByComparingTo(principal);

        // Periods 1 through 11 have identical installments
        BigDecimal standardInstallment = result.items().get(0).totalInstallment();
        for (int i = 0; i < 11; i++) {
            assertThat(result.items().get(i).totalInstallment())
                    .isEqualByComparingTo(standardInstallment);
        }

        // Verify principal portion strictly increases over time
        for (int i = 0; i < 11; i++) {
            assertThat(result.items().get(i + 1).principalDue())
                    .isGreaterThan(result.items().get(i).principalDue());
        }

        // Verify interest portion strictly decreases over time
        for (int i = 0; i < 11; i++) {
            assertThat(result.items().get(i + 1).interestDue())
                    .isLessThan(result.items().get(i).interestDue());
        }

        // Final period brings ending balance to exactly 0.0000
        AmortizationScheduleItem p12 = result.items().get(11);
        assertThat(p12.endingBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Zero Interest Rate: Handled smoothly across both repayment types")
    void testZeroInterestRate() {
        BigDecimal principal = new BigDecimal("60000000.0000");
        BigDecimal zeroRate = BigDecimal.ZERO;
        int termMonths = 6;

        AmortizationResult eqRes = service.calculateSchedule(principal, zeroRate, termMonths, "EQUAL_PRINCIPAL", startDate);
        assertThat(eqRes.totalInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(eqRes.totalPrincipal()).isEqualByComparingTo(principal);

        AmortizationResult emiRes = service.calculateSchedule(principal, zeroRate, termMonths, "ANNUITY_EMI", startDate);
        assertThat(emiRes.totalInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(emiRes.totalPrincipal()).isEqualByComparingTo(principal);
    }

    @Test
    @DisplayName("Input Validations: Rejects invalid parameters")
    void testInputValidations() {
        assertThatThrownBy(() -> service.calculateSchedule(BigDecimal.ZERO, new BigDecimal("0.1"), 12, "ANNUITY_EMI", startDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Principal amount must be positive");

        assertThatThrownBy(() -> service.calculateSchedule(new BigDecimal("100"), new BigDecimal("-0.1"), 12, "ANNUITY_EMI", startDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Annual rate cannot be negative");

        assertThatThrownBy(() -> service.calculateSchedule(new BigDecimal("100"), new BigDecimal("0.1"), 0, "ANNUITY_EMI", startDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Term months must be positive");

        assertThatThrownBy(() -> service.calculateSchedule(new BigDecimal("100"), new BigDecimal("0.1"), 12, "UNKNOWN", startDate))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Unsupported repayment type");
    }
}

package com.cbs.lending.service;

import com.cbs.lending.domain.RepaymentType;
import com.cbs.lending.dto.AmortizationResult;
import com.cbs.lending.dto.AmortizationScheduleItem;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4a: Loan Amortization Schedule Engine.
 * Generates financial repayment schedules according to banking standards:
 * 1. EQUAL_PRINCIPAL (Equal principal monthly, interest calculated on reducing balance)
 * 2. ANNUITY_EMI (Fixed annuity / Equal monthly installments, interest calculated on reducing balance)
 */
@Service
@Slf4j
public class AmortizationScheduleService {

    private static final int SCALE = 4;
    private static final BigDecimal TWELVE = BigDecimal.valueOf(12);

    /**
     * Calculates the amortization schedule based on the repayment type.
     */
    public AmortizationResult calculateSchedule(
            BigDecimal principal,
            BigDecimal annualRate,
            int termMonths,
            String repaymentType,
            LocalDate firstDueDate
    ) {
        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be positive: " + principal);
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) < 0) {
            throw new IllegalArgumentException("Annual rate cannot be negative: " + annualRate);
        }
        if (termMonths <= 0) {
            throw new IllegalArgumentException("Term months must be positive: " + termMonths);
        }
        if (firstDueDate == null) {
            throw new IllegalArgumentException("First due date cannot be null");
        }

        if (RepaymentType.EQUAL_PRINCIPAL.name().equalsIgnoreCase(repaymentType)) {
            return generateEqualPrincipalSchedule(principal, annualRate, termMonths, firstDueDate);
        } else if (RepaymentType.ANNUITY_EMI.name().equalsIgnoreCase(repaymentType)) {
            return generateAnnuityEmiSchedule(principal, annualRate, termMonths, firstDueDate);
        } else {
            throw new IllegalArgumentException("Unsupported repayment type: " + repaymentType);
        }
    }

    /**
     * Generates an EQUAL_PRINCIPAL schedule:
     * - Monthly principal is constant: Principal / N
     * - Monthly interest is calculated on beginning outstanding principal: Remaining * (r / 12)
     * - The final period absorbs rounding residuals to ensure ending balance is exactly 0.0000.
     */
    public AmortizationResult generateEqualPrincipalSchedule(
            BigDecimal principal,
            BigDecimal annualRate,
            int termMonths,
            LocalDate firstDueDate
    ) {
        BigDecimal basePrincipal = principal.divide(BigDecimal.valueOf(termMonths), SCALE, RoundingMode.HALF_UP);
        BigDecimal currentBalance = principal.setScale(SCALE, RoundingMode.HALF_UP);

        List<AmortizationScheduleItem> items = new ArrayList<>(termMonths);
        BigDecimal totalPrincipal = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

        for (int period = 1; period <= termMonths; period++) {
            LocalDate dueDate = firstDueDate.plusMonths(period - 1);
            BigDecimal beginningBalance = currentBalance;

            // Interest for the period on beginning balance
            BigDecimal interestDue = beginningBalance.multiply(annualRate)
                    .divide(TWELVE, SCALE, RoundingMode.HALF_UP);

            // In the final period, principal due absorbs any rounding difference
            BigDecimal principalDue = (period == termMonths)
                    ? currentBalance
                    : basePrincipal.min(currentBalance);

            BigDecimal totalInstallment = principalDue.add(interestDue);
            BigDecimal endingBalance = beginningBalance.subtract(principalDue);

            items.add(AmortizationScheduleItem.builder()
                    .periodNumber(period)
                    .dueDate(dueDate)
                    .beginningBalance(beginningBalance)
                    .principalDue(principalDue)
                    .interestDue(interestDue)
                    .totalInstallment(totalInstallment)
                    .endingBalance(endingBalance)
                    .build());

            totalPrincipal = totalPrincipal.add(principalDue);
            totalInterest = totalInterest.add(interestDue);
            currentBalance = endingBalance;
        }

        return AmortizationResult.builder()
                .repaymentType(RepaymentType.EQUAL_PRINCIPAL.name())
                .totalPrincipal(totalPrincipal)
                .totalInterest(totalInterest)
                .totalPayment(totalPrincipal.add(totalInterest))
                .items(items)
                .build();
    }

    /**
     * Generates an ANNUITY_EMI schedule:
     * - Total monthly installment (EMI) is constant:
     *     EMI = P * [ i * (1 + i)^N ] / [ (1 + i)^N - 1 ], where i = r / 12
     * - Principal portion increases while interest portion decreases over time.
     * - The final period absorbs rounding residuals so remaining balance is exactly 0.0000.
     */
    public AmortizationResult generateAnnuityEmiSchedule(
            BigDecimal principal,
            BigDecimal annualRate,
            int termMonths,
            LocalDate firstDueDate
    ) {
        BigDecimal currentBalance = principal.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal emi;

        if (annualRate.compareTo(BigDecimal.ZERO) == 0) {
            emi = principal.divide(BigDecimal.valueOf(termMonths), SCALE, RoundingMode.HALF_UP);
        } else {
            // i = r / 12 (using high precision)
            double i = annualRate.doubleValue() / 12.0;
            double pow = Math.pow(1.0 + i, termMonths);
            double emiFactor = (i * pow) / (pow - 1.0);
            emi = principal.multiply(BigDecimal.valueOf(emiFactor)).setScale(SCALE, RoundingMode.HALF_UP);
        }

        List<AmortizationScheduleItem> items = new ArrayList<>(termMonths);
        BigDecimal totalPrincipal = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);
        BigDecimal totalInterest = BigDecimal.ZERO.setScale(SCALE, RoundingMode.HALF_UP);

        for (int period = 1; period <= termMonths; period++) {
            LocalDate dueDate = firstDueDate.plusMonths(period - 1);
            BigDecimal beginningBalance = currentBalance;

            // Interest for the period on beginning balance
            BigDecimal interestDue = beginningBalance.multiply(annualRate)
                    .divide(TWELVE, SCALE, RoundingMode.HALF_UP);

            BigDecimal principalDue;
            if (period == termMonths) {
                // Final period pays off all remaining principal
                principalDue = currentBalance;
            } else {
                principalDue = emi.subtract(interestDue).min(currentBalance);
            }

            BigDecimal totalInstallment = principalDue.add(interestDue);
            BigDecimal endingBalance = beginningBalance.subtract(principalDue);

            items.add(AmortizationScheduleItem.builder()
                    .periodNumber(period)
                    .dueDate(dueDate)
                    .beginningBalance(beginningBalance)
                    .principalDue(principalDue)
                    .interestDue(interestDue)
                    .totalInstallment(totalInstallment)
                    .endingBalance(endingBalance)
                    .build());

            totalPrincipal = totalPrincipal.add(principalDue);
            totalInterest = totalInterest.add(interestDue);
            currentBalance = endingBalance;
        }

        return AmortizationResult.builder()
                .repaymentType(RepaymentType.ANNUITY_EMI.name())
                .totalPrincipal(totalPrincipal)
                .totalInterest(totalInterest)
                .totalPayment(totalPrincipal.add(totalInterest))
                .items(items)
                .build();
    }
}

package com.cbs.eod.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Report containing the full Trial Balance reconciliation breakdown.
 */
@Builder
public record TrialBalanceReport(
        LocalDate workingDate,
        BigDecimal macroDebitSum,
        BigDecimal macroCreditSum,
        BigDecimal subledgerDebitSum,
        BigDecimal subledgerCreditSum,
        BigDecimal gl4211DebitSum,
        BigDecimal gl4211CreditSum,
        BigDecimal liveAccountTotalBalance,
        BigDecimal bucketTotalClosingBalance,
        boolean isBalanced,
        List<TrialBalanceEntry> entries,
        OffsetDateTime reconciledAt,
        String message
) {
    public BigDecimal totalDebit() {
        return macroDebitSum;
    }

    public BigDecimal totalCredit() {
        return macroCreditSum;
    }

    public boolean balanced() {
        return isBalanced;
    }
}

package com.cbs.eod.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Detailed accrual calculation result for an individual CASA account.
 */
@Builder
public record AccountAccrualDetail(
        Long accountId,
        String accountNumber,
        String productCode,
        BigDecimal ledgerBalance,
        BigDecimal annualRate,
        String dayCountBasis,
        BigDecimal dailyInterest,
        BigDecimal updatedAccruedInterest
) {
}

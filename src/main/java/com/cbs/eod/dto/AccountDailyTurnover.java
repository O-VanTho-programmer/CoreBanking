package com.cbs.eod.dto;

import lombok.Builder;

import java.math.BigDecimal;

/**
 * Aggregated daily debit and credit turnovers for an individual account.
 */
@Builder
public record AccountDailyTurnover(
        Long accountId,
        BigDecimal dailyDebitSum,
        BigDecimal dailyCreditSum
) {
    public static AccountDailyTurnover empty(Long accountId) {
        return new AccountDailyTurnover(accountId, BigDecimal.ZERO.setScale(4), BigDecimal.ZERO.setScale(4));
    }
}

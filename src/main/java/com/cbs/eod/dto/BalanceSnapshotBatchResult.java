package com.cbs.eod.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Summary result of an EOD Balance Snapshot Bucket run.
 */
@Builder
public record BalanceSnapshotBatchResult(
        LocalDate workingDate,
        int totalAccountsSnapshotted,
        BigDecimal totalClosingBalance,
        BigDecimal totalDailyDebitSum,
        BigDecimal totalDailyCreditSum,
        BigDecimal totalDailyAccruedInterest,
        OffsetDateTime completedAt
) {
}

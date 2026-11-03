package com.cbs.eod.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * High-level execution report of the Spring Batch EOD Pipeline run.
 */
@Builder
public record EodExecutionReport(
        Long jobExecutionId,
        String status,
        LocalDate previousWorkingDate,
        LocalDate newWorkingDate,
        long durationMs,
        TrialBalanceReport trialBalanceReport,
        OffsetDateTime completedAt
) {
}

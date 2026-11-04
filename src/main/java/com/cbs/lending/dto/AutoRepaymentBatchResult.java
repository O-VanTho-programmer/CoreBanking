package com.cbs.lending.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Summary result of EOD automated loan repayment execution.
 */
@Builder
public record AutoRepaymentBatchResult(
        LocalDate workingDate,
        int totalDueSchedulesProcessed,
        int fullyPaidSchedulesCount,
        int partiallyPaidSchedulesCount,
        int fullyPaidOffLoansCount,
        BigDecimal totalPrincipalCollected,
        BigDecimal totalInterestCollected,
        BigDecimal totalPenaltyCollected,
        OffsetDateTime completedAt
) {
}

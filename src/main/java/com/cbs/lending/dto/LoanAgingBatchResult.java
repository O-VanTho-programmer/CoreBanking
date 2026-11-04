package com.cbs.lending.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Summary result of EOD loan classification, overdue aging, and provisioning execution.
 */
@Builder
public record LoanAgingBatchResult(
        LocalDate workingDate,
        int totalLoansEvaluated,
        int overdueLoansCount,
        int classifiedGroup2Count,
        int classifiedGroup3To5Count,
        BigDecimal totalOverduePrincipalTransferred,
        BigDecimal totalGeneralProvisionCalculated,
        BigDecimal totalSpecificProvisionCalculated,
        BigDecimal totalProvisionReleased,
        OffsetDateTime completedAt
) {
}

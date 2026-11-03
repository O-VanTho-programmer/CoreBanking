package com.cbs.lending.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record AmortizationScheduleItem(
        int periodNumber,
        LocalDate dueDate,
        BigDecimal beginningBalance,
        BigDecimal principalDue,
        BigDecimal interestDue,
        BigDecimal totalInstallment,
        BigDecimal endingBalance
) {
}

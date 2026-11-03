package com.cbs.lending.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.util.List;

@Builder
public record AmortizationResult(
        String repaymentType,
        BigDecimal totalPrincipal,
        BigDecimal totalInterest,
        BigDecimal totalPayment,
        List<AmortizationScheduleItem> items
) {
}

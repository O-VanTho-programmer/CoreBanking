package com.cbs.lending.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

@Builder
public record CreateLoanContractCommand(
        Long customerId,
        Long linkedCasaAccountId,
        String branchCode,
        String productCode,
        BigDecimal principalAmount,
        Integer termMonths,
        BigDecimal interestMargin,
        BigDecimal interestRateOverride,
        String repaymentTypeOverride,
        LocalDate firstDueDate
) {
}

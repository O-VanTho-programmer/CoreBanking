package com.cbs.depositing.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Input request containing all business parameters required by the Posting Engine
 * to evaluate and generate balanced journal entries.
 */
@Builder(toBuilder = true)
public record PostingRequest(
        String trxCode,
        String currency,
        LocalDate workingDate,
        BigDecimal trxAmount,
        BigDecimal feeAmount,
        BigDecimal taxAmount,
        BigDecimal penaltyAmount,
        BigDecimal principalAmount,
        BigDecimal interestAmount,
        Long customerAccountId,
        String customerGlCode,
        Long targetAccountId,
        String targetGlCode,
        Long loanId,
        String branchCode,
        String productCode,
        String narrationOverride
) {
    public PostingRequest {
        if (trxCode == null || trxCode.isBlank()) {
            throw new IllegalArgumentException("trxCode cannot be blank");
        }
        if (currency == null || currency.isBlank()) {
            throw new IllegalArgumentException("currency cannot be blank");
        }
        if (workingDate == null) {
            throw new IllegalArgumentException("workingDate cannot be null");
        }
        if (trxAmount == null || trxAmount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("trxAmount must be strictly positive");
        }
    }
}

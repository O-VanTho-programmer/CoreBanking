package com.cbs.transaction.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Command for cash deposit at branch counter (CASH_DEP).
 */
@Builder
public record DepositCommand(
        @NotBlank
        @ValidCodeList(group = "CHANNEL")
        String channelId,

        @NotBlank
        String idempotencyKey,

        @NotBlank
        String accountNumber,

        @NotNull
        BigDecimal amount,

        String currency,

        String branchCode,

        String makerId,

        String narration
) {
    public DepositCommand {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Deposit amount must be strictly positive");
        }
        if (branchCode == null || branchCode.isBlank()) {
            branchCode = "VN001";
        }
        if (makerId == null || makerId.isBlank()) {
            makerId = "TELLER";
        }
    }
}

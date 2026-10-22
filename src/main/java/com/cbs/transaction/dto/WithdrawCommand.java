package com.cbs.transaction.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Command for cash withdrawal at branch counter or ATM (CASH_WDR).
 */
@Builder
public record WithdrawCommand(
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
    public WithdrawCommand {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Withdrawal amount must be strictly positive");
        }
        if (branchCode == null || branchCode.isBlank()) {
            branchCode = "VN001";
        }
        if (makerId == null || makerId.isBlank()) {
            makerId = "TELLER";
        }
    }
}

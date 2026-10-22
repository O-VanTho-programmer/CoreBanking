package com.cbs.transaction.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Command to execute an internal transfer between two customer accounts.
 */
@Builder
public record TransferCommand(
        @NotBlank(message = "channelId is required")
        @ValidCodeList(group = "CHANNEL")
        String channelId,

        @NotBlank(message = "idempotencyKey is required")
        String idempotencyKey,

        @NotBlank(message = "fromAccountNumber is required")
        String fromAccountNumber,

        @NotBlank(message = "toAccountNumber is required")
        String toAccountNumber,

        @NotNull(message = "amount is required")
        BigDecimal amount,

        String currency,

        BigDecimal feeAmount,

        String makerId,

        String narration
) {
    public TransferCommand {
        if (amount != null && amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive");
        }
        if (makerId == null || makerId.isBlank()) {
            makerId = "SYSTEM";
        }
    }
}

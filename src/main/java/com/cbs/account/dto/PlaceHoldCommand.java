package com.cbs.account.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Command for placing a funds earmark / hold on an account.
 */
@Builder
public record PlaceHoldCommand(
        @NotNull(message = "accountId is mandatory")
        Long accountId,

        @NotNull(message = "amount is mandatory")
        BigDecimal amount,

        @ValidCodeList(group = "HOLD_REASON")
        String reasonCode,

        String referenceId
) {
    public PlaceHoldCommand {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Hold amount must be strictly greater than zero");
        }
        if (reasonCode == null || reasonCode.isBlank()) {
            reasonCode = "CARD_AUTH";
        }
    }
}

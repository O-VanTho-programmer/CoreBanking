package com.cbs.transaction.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Command to execute a financial reversal (Storno) of a previously completed transaction.
 */
@Builder
public record ReverseCommand(
        @NotBlank(message = "channelId is mandatory")
        String channelId,

        @NotBlank(message = "idempotencyKey is mandatory")
        String idempotencyKey,

        @NotBlank(message = "originalTrxNumber is mandatory")
        String originalTrxNumber,

        @NotBlank(message = "reason is mandatory")
        String reason,

        @NotBlank(message = "makerId is mandatory")
        String makerId
) {}

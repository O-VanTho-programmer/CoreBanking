package com.cbs.lending.dto;

import lombok.Builder;

/**
 * Command to execute loan disbursement into customer's linked CASA account.
 */
@Builder
public record DisburseLoanCommand(
        Long loanId,
        String channelId,
        String idempotencyKey,
        String branchCode,
        String makerId,
        String narrationOverride
) {
    public DisburseLoanCommand {
        if (loanId == null) {
            throw new IllegalArgumentException("loanId cannot be null");
        }
        if (channelId == null || channelId.isBlank()) {
            throw new IllegalArgumentException("channelId cannot be blank");
        }
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            throw new IllegalArgumentException("idempotencyKey cannot be blank");
        }
    }
}

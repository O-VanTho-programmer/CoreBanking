package com.cbs.transaction.dto;

import com.cbs.accounting.dto.JournalLeg;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Receipt returned to clients for completed or replayed transactions.
 */
@Builder
@JsonIgnoreProperties(ignoreUnknown = true)
public record TransactionReceipt(
        Long trxId,
        String trxNumber,
        String channelId,
        String idempotencyKey,
        String trxCode,
        LocalDate workingDate,
        String status,
        String currency,
        BigDecimal totalAmount,
        BigDecimal feeAmount,
        BigDecimal fromAccountAvailableBalance,
        BigDecimal toAccountAvailableBalance,
        List<JournalLeg> legs,
        OffsetDateTime timestamp,
        Long originalTrxId,
        String originalTrxNumber,
        String reversalReason,
        Long journalId,
        List<Long> journalEntryIds
) {
    public BigDecimal amount() {
        return totalAmount;
    }

    public String trxType() {
        return trxCode;
    }
}

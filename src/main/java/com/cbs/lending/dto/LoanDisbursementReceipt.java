package com.cbs.lending.dto;

import com.cbs.depositing.dto.JournalLeg;
import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Receipt returned upon successful loan disbursement into customer's CASA account.
 */
@Builder
public record LoanDisbursementReceipt(
        Long trxId,
        String trxNumber,
        Long loanId,
        String loanAccountNumber,
        Long casaAccountId,
        String casaAccountNumber,
        BigDecimal disbursedAmount,
        BigDecimal casaAvailableBalance,
        String currency,
        LocalDate workingDate,
        OffsetDateTime disbursedAt,
        String status,
        List<JournalLeg> legs
) {
}

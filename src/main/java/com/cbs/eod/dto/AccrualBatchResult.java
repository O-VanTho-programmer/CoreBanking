package com.cbs.eod.dto;

import lombok.Builder;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Summary result of an EOD Daily Savings Interest Accrual run.
 */
@Builder
public record AccrualBatchResult(
        LocalDate workingDate,
        int totalAccountsProcessed,
        int totalAccountsAccrued,
        BigDecimal totalInterestAccrued,
        Long journalTrxId,
        String trxNumber,
        List<AccountAccrualDetail> accountDetails
) {
}

package com.cbs.eod.dto;

import java.math.BigDecimal;

public record TrialBalanceEntry(
        String accountNumber,
        String accountName,
        BigDecimal debitBalance,
        BigDecimal creditBalance,
        BigDecimal netBalance
) {}

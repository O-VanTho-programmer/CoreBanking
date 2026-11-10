package com.cbs.deposit.dto;

import java.math.BigDecimal;

public record AccountDetailResponse(
        Long accountId,
        String accountNumber,
        Long customerId,
        String customerName,
        String glCode,
        String currency,
        BigDecimal ledgerBalance,
        BigDecimal holdBalance,
        BigDecimal availableBalance,
        String status,
        String branchCode,
        String productCode
) {}

package com.cbs.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Immutable representation of a single balanced accounting journal leg.
 */
public record JournalLeg(
        int entryGroup,
        int entryIndex,
        Long accountId,        // NULL for internal bank GL accounts; non-null for customer CASA accounts
        String glCode,         // Physical GL code from Chart of Accounts (e.g. "1011", "4211")
        String drCr,           // "D" (Debit) or "C" (Credit)
        BigDecimal amount,     // Always positive with scale 4
        String currency,       // ISO-4217 Currency (e.g. "VND")
        LocalDate valueDate,   // Accounting value date
        String narration       // Transaction narration / description
) {
    public JournalLeg {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Journal leg amount must be strictly greater than zero");
        }
        if (!"D".equals(drCr) && !"C".equals(drCr)) {
            throw new IllegalArgumentException("drCr must be either 'D' or 'C', received: " + drCr);
        }
    }
}

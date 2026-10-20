package com.cbs.accounting.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Output of the Posting Engine containing generated and verified balanced journal legs.
 */
public record PostingResult(
        String trxCode,
        LocalDate workingDate,
        String currency,
        BigDecimal totalDebit,
        BigDecimal totalCredit,
        List<JournalLeg> legs
) {
    public PostingResult {
        if (totalDebit == null || totalCredit == null || totalDebit.compareTo(totalCredit) != 0) {
            throw new IllegalArgumentException("PostingResult must have totalDebit equal to totalCredit");
        }
        legs = List.copyOf(legs);
    }
}

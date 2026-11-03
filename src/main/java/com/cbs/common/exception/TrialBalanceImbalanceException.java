package com.cbs.common.exception;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thrown when General Ledger Macro Equilibrium fails (Total Debit != Total Credit).
 */
public class TrialBalanceImbalanceException extends RuntimeException {

    public TrialBalanceImbalanceException(LocalDate workingDate, BigDecimal totalDebit, BigDecimal totalCredit) {
        super(String.format("General Ledger Macro Equilibrium FAILED on %s: Total Debit=%s, Total Credit=%s, Discrepancy=%s",
                workingDate, totalDebit, totalCredit, totalDebit.subtract(totalCredit).abs()));
    }
}

package com.cbs.common.exception;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Thrown when Sub-ledger (Customer Accounts / Buckets) does not reconcile with General Ledger Control accounts.
 */
public class SubledgerGlDiscrepancyException extends RuntimeException {

    public SubledgerGlDiscrepancyException(LocalDate workingDate, String component, BigDecimal subledgerAmount, BigDecimal glAmount) {
        super(String.format("Sub-ledger vs GL Reconciliation FAILED on %s for %s: Subledger=%s, GL=%s, Discrepancy=%s",
                workingDate, component, subledgerAmount, glAmount, subledgerAmount.subtract(glAmount).abs()));
    }
}

package com.cbs.common.exception;

import java.math.BigDecimal;

/**
 * Thrown when a financial transaction violates Invariant 1 (The Double-Entry Equilibrium):
 * SUM(Debit) != SUM(Credit).
 */
public class UnbalancedTransactionException extends RuntimeException {

    private final String trxCode;
    private final Integer entryGroup;
    private final BigDecimal totalDebit;
    private final BigDecimal totalCredit;

    public UnbalancedTransactionException(String trxCode, Integer entryGroup, BigDecimal totalDebit, BigDecimal totalCredit) {
        super(String.format("Unbalanced transaction for trxCode='%s' in group %s: Debit=%s, Credit=%s (Diff=%s)",
                trxCode,
                entryGroup == null ? "ALL" : entryGroup,
                totalDebit,
                totalCredit,
                totalDebit.subtract(totalCredit)));
        this.trxCode = trxCode;
        this.entryGroup = entryGroup;
        this.totalDebit = totalDebit;
        this.totalCredit = totalCredit;
    }

    public String getTrxCode() {
        return trxCode;
    }

    public Integer getEntryGroup() {
        return entryGroup;
    }

    public BigDecimal getTotalDebit() {
        return totalDebit;
    }

    public BigDecimal getTotalCredit() {
        return totalCredit;
    }
}

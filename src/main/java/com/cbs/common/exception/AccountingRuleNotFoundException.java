package com.cbs.common.exception;

import java.time.LocalDate;

/**
 * Thrown when no active rule matrix is configured for a transaction code on the specified working date.
 */
public class AccountingRuleNotFoundException extends RuntimeException {

    private final String trxCode;
    private final LocalDate workingDate;

    public AccountingRuleNotFoundException(String trxCode, LocalDate workingDate) {
        super(String.format("No active accounting rule matrix found for trxCode='%s' on workingDate='%s'",
                trxCode, workingDate));
        this.trxCode = trxCode;
        this.workingDate = workingDate;
    }

    public String getTrxCode() {
        return trxCode;
    }

    public LocalDate getWorkingDate() {
        return workingDate;
    }
}

package com.cbs.lending.domain;

/**
 * Standard Loan Account Status Codes mapped to CODELIST group 'LOAN_STATUS'.
 */
public enum LoanStatus {
    APPROVED,
    ACTIVE,
    OVERDUE,
    PAID_OFF,
    BAD_DEBT
}

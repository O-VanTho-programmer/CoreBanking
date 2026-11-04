package com.cbs.common.exception;

public class LoanNotApprovedException extends RuntimeException {
    public LoanNotApprovedException(Long loanId, String currentStatus) {
        super(String.format("Loan account %d is not in APPROVED status. Current status: '%s'", loanId, currentStatus));
    }
}

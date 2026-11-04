package com.cbs.common.exception;

public class LoanAlreadyDisbursedException extends RuntimeException {
    public LoanAlreadyDisbursedException(Long loanId) {
        super("Loan account " + loanId + " has already been disbursed");
    }

    public LoanAlreadyDisbursedException(Long loanId, String status) {
        super(String.format("Loan account %d cannot be disbursed because it is already in '%s' status", loanId, status));
    }
}

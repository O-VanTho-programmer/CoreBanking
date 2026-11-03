package com.cbs.common.exception;

public class LoanAccountNotFoundException extends RuntimeException {
    public LoanAccountNotFoundException(Long loanId) {
        super("Loan account not found with id: " + loanId);
    }

    public LoanAccountNotFoundException(String loanAccountNumber) {
        super("Loan account not found with account number: " + loanAccountNumber);
    }
}

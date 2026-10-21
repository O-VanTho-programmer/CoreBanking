package com.cbs.common.exception;

public class AccountNotFoundException extends RuntimeException {
    public AccountNotFoundException(String message) {
        super(message);
    }

    public static AccountNotFoundException byId(Long accountId) {
        return new AccountNotFoundException("Account not found with id: " + accountId);
    }

    public static AccountNotFoundException byNumber(String accountNumber) {
        return new AccountNotFoundException("Account not found with account number: " + accountNumber);
    }
}

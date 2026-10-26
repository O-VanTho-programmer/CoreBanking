package com.cbs.common.exception;

public class TransactionAlreadyReversedException extends RuntimeException {
    public TransactionAlreadyReversedException(String trxNumber) {
        super(String.format("Transaction '%s' has already been reversed", trxNumber));
    }
}

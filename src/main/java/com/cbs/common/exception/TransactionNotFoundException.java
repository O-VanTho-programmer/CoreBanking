package com.cbs.common.exception;

public class TransactionNotFoundException extends RuntimeException {
    public TransactionNotFoundException(String trxNumber) {
        super(String.format("Transaction '%s' was not found in active journal", trxNumber));
    }

    public TransactionNotFoundException(Long trxId) {
        super(String.format("Transaction with ID %d was not found in active journal", trxId));
    }
}

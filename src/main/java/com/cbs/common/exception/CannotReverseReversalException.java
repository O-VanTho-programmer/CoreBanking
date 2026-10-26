package com.cbs.common.exception;

public class CannotReverseReversalException extends RuntimeException {
    public CannotReverseReversalException(String trxNumber) {
        super(String.format("Transaction '%s' is a reversal transaction and cannot be reversed", trxNumber));
    }
}

package com.cbs.common.exception;

public class InvalidLoanParameterException extends RuntimeException {
    public InvalidLoanParameterException(String message) {
        super(message);
    }
}

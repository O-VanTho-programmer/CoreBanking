package com.cbs.common.exception;

public class LoanProductNotFoundException extends RuntimeException {
    public LoanProductNotFoundException(String productCode) {
        super("Credit product not found or inactive: " + productCode);
    }
}

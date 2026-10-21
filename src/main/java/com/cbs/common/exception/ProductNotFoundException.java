package com.cbs.common.exception;

public class ProductNotFoundException extends RuntimeException {
    public ProductNotFoundException(String productCode) {
        super("Product not found or inactive with code: " + productCode);
    }
}

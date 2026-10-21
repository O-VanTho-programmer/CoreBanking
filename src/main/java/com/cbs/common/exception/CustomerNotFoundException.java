package com.cbs.common.exception;

public class CustomerNotFoundException extends RuntimeException {
    public CustomerNotFoundException(String message) {
        super(message);
    }

    public static CustomerNotFoundException byId(Long customerId) {
        return new CustomerNotFoundException("Customer not found with id: " + customerId);
    }

    public static CustomerNotFoundException byCif(String cifNumber) {
        return new CustomerNotFoundException("Customer not found with CIF number: " + cifNumber);
    }
}

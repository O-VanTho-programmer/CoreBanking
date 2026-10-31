package com.cbs.common.exception;

import java.time.LocalDate;

public class InFlightTransactionFoundException extends RuntimeException {

    public InFlightTransactionFoundException(long count, LocalDate workingDate) {
        super(String.format("Cannot archive data: Found %d IN_FLIGHT transactions on or before %s", count, workingDate));
    }
}

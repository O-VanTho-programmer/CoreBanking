package com.cbs.common.exception;

import java.time.LocalDate;

public class DuplicateAccrualException extends RuntimeException {

    public DuplicateAccrualException(LocalDate workingDate) {
        super(String.format("Daily interest accrual has already been executed for working date: %s", workingDate));
    }
}

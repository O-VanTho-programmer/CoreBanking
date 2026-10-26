package com.cbs.common.exception;

import java.time.LocalDate;

public class ReversalWorkingDateMismatchException extends RuntimeException {
    public ReversalWorkingDateMismatchException(String trxNumber, LocalDate origDate, LocalDate curDate) {
        super(String.format("Cannot reverse transaction '%s': original working date '%s' does not match current working date '%s'. Same-day storno only.",
                trxNumber, origDate, curDate));
    }
}

package com.cbs.common.exception;

import java.time.LocalDate;

public class DuplicateSnapshotException extends RuntimeException {

    public DuplicateSnapshotException(LocalDate workingDate) {
        super(String.format("Balance snapshot bucket has already been created for working date: %s", workingDate));
    }
}

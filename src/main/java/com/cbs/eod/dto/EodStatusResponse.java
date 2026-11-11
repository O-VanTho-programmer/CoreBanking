package com.cbs.eod.dto;

import java.time.LocalDate;

public record EodStatusResponse(
        LocalDate currentWorkingDate,
        LocalDate nextWorkingDate,
        boolean isCutoff
) {
    public LocalDate workingDate() {
        return currentWorkingDate;
    }

    public boolean cutoffClosed() {
        return isCutoff;
    }
}

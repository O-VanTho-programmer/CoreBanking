package com.cbs.eod.dto;

import java.time.LocalDate;

public record EodStatusResponse(
        LocalDate currentWorkingDate,
        LocalDate nextWorkingDate,
        boolean isCutoff
) {}

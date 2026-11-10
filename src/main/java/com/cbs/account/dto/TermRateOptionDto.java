package com.cbs.account.dto;

import java.math.BigDecimal;

public record TermRateOptionDto(
        Integer termMonths,
        BigDecimal baseRate,
        String label
) {}

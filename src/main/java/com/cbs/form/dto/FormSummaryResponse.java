package com.cbs.form.dto;

public record FormSummaryResponse(
        String formCode,
        String trxCode,
        String module,
        String formTitle,
        Integer version
) {}

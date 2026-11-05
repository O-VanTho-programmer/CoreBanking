package com.cbs.form.dto;

import java.util.Map;
import java.util.Set;

public record FormEvaluateResponse(
        Map<String, Object> updates,
        Map<String, String> errors,
        Set<String> disabledFields,
        Set<String> hiddenFields
) {}

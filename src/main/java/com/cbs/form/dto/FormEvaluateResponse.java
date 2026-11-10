package com.cbs.form.dto;

import java.util.List;
import java.util.Map;
import java.util.Set;

public record FormEvaluateResponse(
        Map<String, Object> updates,
        Map<String, String> errors,
        Set<String> disabledFields,
        Set<String> hiddenFields,
        Map<String, List<Map<String, Object>>> fieldOptions
) {
    public FormEvaluateResponse(
            Map<String, Object> updates,
            Map<String, String> errors,
            Set<String> disabledFields,
            Set<String> hiddenFields) {
        this(updates, errors, disabledFields, hiddenFields, Map.of());
    }
}

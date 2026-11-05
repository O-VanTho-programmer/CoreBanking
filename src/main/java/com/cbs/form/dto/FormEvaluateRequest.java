package com.cbs.form.dto;

import java.util.List;
import java.util.Map;

public record FormEvaluateRequest(
        String formCode,
        String triggerField,
        List<String> stepCodes,
        Map<String, Object> formData
) {}

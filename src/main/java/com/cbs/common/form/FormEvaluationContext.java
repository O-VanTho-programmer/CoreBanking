package com.cbs.common.form;

import lombok.Getter;

import java.math.BigDecimal;
import java.util.*;

/**
 * Context object passed through FormStepHandlers during server-driven evaluation.
 */
@Getter
public class FormEvaluationContext {

    private final String formCode;
    private final String triggerField;
    private final Map<String, Object> formData;

    private final Map<String, Object> updates = new LinkedHashMap<>();
    private final Map<String, String> errors = new LinkedHashMap<>();
    private final Set<String> disabledFields = new LinkedHashSet<>();
    private final Set<String> hiddenFields = new LinkedHashSet<>();

    public FormEvaluationContext(String formCode, String triggerField, Map<String, Object> formData) {
        this.formCode = formCode;
        this.triggerField = triggerField;
        this.formData = (formData != null) ? new HashMap<>(formData) : new HashMap<>();
    }

    public String getString(String fieldName) {
        Object val = formData.get(fieldName);
        return (val != null) ? val.toString().trim() : null;
    }

    public BigDecimal getBigDecimal(String fieldName) {
        Object val = formData.get(fieldName);
        if (val == null) return null;
        try {
            return new BigDecimal(val.toString().trim().replace(",", ""));
        } catch (Exception e) {
            return null;
        }
    }

    public Integer getInteger(String fieldName) {
        Object val = formData.get(fieldName);
        if (val == null) return null;
        try {
            return Integer.parseInt(val.toString().trim());
        } catch (Exception e) {
            return null;
        }
    }

    public void putUpdate(String fieldName, Object value) {
        updates.put(fieldName, value);
        formData.put(fieldName, value); // update snapshot for subsequent steps in pipeline
    }

    public void putError(String fieldName, String errorMsg) {
        errors.put(fieldName, errorMsg);
    }

    public void putDisabled(String fieldName) {
        disabledFields.add(fieldName);
    }

    public void putHidden(String fieldName) {
        hiddenFields.add(fieldName);
    }

    public boolean hasErrors() {
        return !errors.isEmpty();
    }
}

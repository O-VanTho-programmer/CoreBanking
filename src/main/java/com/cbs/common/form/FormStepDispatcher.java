package com.cbs.common.form;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Dispatches form evaluation steps to registered FormStepHandler beans.
 * Completely eliminates god-classes and switch-cases via Spring Bean Registry.
 */
@Service
@Slf4j
public class FormStepDispatcher {

    private final Map<String, FormStepHandler> handlerMap;

    public FormStepDispatcher(List<FormStepHandler> handlers) {
        this.handlerMap = handlers.stream()
                .collect(Collectors.toMap(FormStepHandler::getStepCode, Function.identity()));
        log.info("FormStepDispatcher initialized with {} step handlers: {}",
                handlerMap.size(), handlerMap.keySet());
    }

    public FormEvaluationContext dispatch(String formCode, String triggerField, List<String> stepCodes, Map<String, Object> formData) {
        FormEvaluationContext context = new FormEvaluationContext(formCode, triggerField, formData);

        if (stepCodes == null || stepCodes.isEmpty()) {
            return context;
        }

        for (String stepCode : stepCodes) {
            FormStepHandler handler = handlerMap.get(stepCode);
            if (handler != null) {
                try {
                    handler.execute(context);
                } catch (Exception e) {
                    log.error("Error executing FormStepHandler [stepCode='{}']: {}", stepCode, e.getMessage(), e);
                    context.putError(triggerField, "Lỗi xử lý nghiệp vụ bước '" + stepCode + "': " + e.getMessage());
                    break;
                }
            } else {
                log.warn("No FormStepHandler found for stepCode='{}'", stepCode);
            }
        }

        return context;
    }
}

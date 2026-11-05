package com.cbs.common.form;

/**
 * Strategy interface for modular, isolated form workflow steps.
 * Implemented as individual Spring @Component beans across domain packages.
 */
public interface FormStepHandler {

    /**
     * Unique StepCode identifier matching the form schema's step list.
     */
    String getStepCode();

    /**
     * Executes business logic, enriching context.putUpdate() and context.putError().
     */
    void execute(FormEvaluationContext context);
}

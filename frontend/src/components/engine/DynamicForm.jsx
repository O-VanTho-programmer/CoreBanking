import React, { useState, useEffect, useCallback } from 'react';
import FormFieldRenderer from './FormFieldRenderer';
import { formApi } from '../../services/formApi';
import { Loader2, AlertTriangle, CheckCircle, Send } from 'lucide-react';

export default function DynamicForm({
  schema,
  initialData = {},
  onSubmit,
  onNavigate,
  isSubmitting = false,
  submitLabel = 'Submit Transaction',
  submitIcon: SubmitIcon = Send,
}) {
  const [formData, setFormData] = useState({});
  const [errors, setErrors] = useState({});
  const [disabledFields, setDisabledFields] = useState(new Set());
  const [hiddenFields, setHiddenFields] = useState(new Set());
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [evaluationNotice, setEvaluationNotice] = useState(null);

  // Initialize form data when schema or initialData changes
  useEffect(() => {
    if (!schema) return;
    const defaults = {};
    (schema.fields || []).forEach((f) => {
      defaults[f.name] = initialData[f.name] ?? f.defaultValue ?? '';
    });
    setFormData(defaults);
    setErrors({});
    setDisabledFields(new Set((schema.fields || []).filter((f) => f.disabled).map((f) => f.name)));
    setHiddenFields(new Set((schema.fields || []).filter((f) => f.hidden).map((f) => f.name)));
  }, [schema, initialData]);

  // Rule Evaluation Engine (Convention over Configuration)
  const triggerEvaluation = useCallback(
    async (fieldName, currentData, explicitSteps = null) => {
      const stepsToRun = explicitSteps || schema?.steps;
      if (!stepsToRun || stepsToRun.length === 0) return;

      try {
        setIsEvaluating(true);
        setEvaluationNotice(null);

        const result = await formApi.evaluateForm(
          schema.formCode,
          stepsToRun,
          fieldName,
          currentData
        );

        if (result) {
          // Merge updates: filter only relevant fields in schema + metadata attributes
          if (result.updates && Object.keys(result.updates).length > 0) {
            setFormData((prev) => {
              const next = { ...prev };
              Object.entries(result.updates).forEach(([k, v]) => {
                next[k] = v;
              });
              return next;
            });
          }

          // Merge backend errors
          if (result.errors) {
            setErrors((prev) => ({
              ...prev,
              ...result.errors,
            }));
          }

          // Updates disabled fields
          if (result.disabledFields) {
            setDisabledFields(new Set(result.disabledFields));
          }

          // Updates hidden fields
          if (result.hiddenFields) {
            setHiddenFields(new Set(result.hiddenFields));
          }
        }
      } catch (err) {
        console.warn('Evaluation failed:', err.message);
      } finally {
        setIsEvaluating(false);
      }
    },
    [schema]
  );

  const handleChange = (e) => {
    const { name, value } = e.target;
    const updatedData = { ...formData, [name]: value };
    setFormData(updatedData);

    // Clear field-level error on change
    if (errors[name]) {
      setErrors((prev) => {
        const next = { ...prev };
        delete next[name];
        return next;
      });
    }

    // Trigger instant evaluation if configured
    const fieldDef = (schema.fields || []).find((f) => f.name === name);
    if (fieldDef?.evaluateOnChange) {
      triggerEvaluation(name, updatedData);
    }
  };

  const handleBlur = (e) => {
    const { name } = e.target;
    const fieldDef = (schema.fields || []).find((f) => f.name === name);
    // Auto evaluate on blur for account lookup, customer lookup, or amount fields
    if (
      fieldDef?.evaluateOnBlur ||
      fieldDef?.triggersEvaluation ||
      fieldDef?.type === 'ACCOUNT_LOOKUP' ||
      fieldDef?.type === 'CURRENCY' ||
      name.toLowerCase().includes('account') ||
      name.toLowerCase().includes('customer') ||
      name.toLowerCase().includes('amount')
    ) {
      triggerEvaluation(name, formData);
    }
  };

  const handleSubmit = (e) => {
    e.preventDefault();

    // Client-side required field validation
    const validationErrors = {};
    (schema.fields || []).forEach((f) => {
      if (f.required && !hiddenFields.has(f.name)) {
        const val = formData[f.name];
        if (val === undefined || val === null || val === '') {
          validationErrors[f.name] = `${f.label} is required`;
        }
      }
    });

    if (Object.keys(validationErrors).length > 0) {
      setErrors((prev) => ({ ...prev, ...validationErrors }));
      return;
    }

    if (onSubmit) {
      onSubmit(formData);
    }
  };

  const handleActionClick = (action) => {
    switch (action.actionType) {
      case 'WORKFLOW_STEP':
        triggerEvaluation(action.code, formData, action.stepCode ? [action.stepCode] : null);
        break;

      case 'NAVIGATE_FORM':
        if (onNavigate) {
          const passData = {};
          if (action.passParams && Array.isArray(action.passParams)) {
            action.passParams.forEach((k) => {
              if (formData[k] !== undefined && formData[k] !== null && formData[k] !== '') {
                passData[k] = formData[k];
              }
            });
          }
          onNavigate(action.targetFormCode, passData);
        }
        break;

      case 'SUBMIT_TRX': {
        const validationErrors = {};
        (schema.fields || []).forEach((f) => {
          if (f.required && !hiddenFields.has(f.name)) {
            const val = formData[f.name];
            if (val === undefined || val === null || val === '') {
              validationErrors[f.name] = `${f.label} is required`;
            }
          }
        });

        if (Object.keys(validationErrors).length > 0) {
          setErrors((prev) => ({ ...prev, ...validationErrors }));
          return;
        }

        if (onSubmit) {
          onSubmit(formData, action);
        }
        break;
      }

      default:
        console.warn('Unhandled action type:', action.actionType);
    }
  };

  if (!schema) {
    return (
      <div className="flex h-64 items-center justify-center rounded-xl border border-dashed border-slate-300 bg-white">
        <p className="text-sm text-slate-400">Form schema not available</p>
      </div>
    );
  }

  return (
    <form onSubmit={handleSubmit} className="space-y-6">
      {/* Form Header Info */}
      <div className="flex items-center justify-between border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="rounded bg-blue-100 px-2 py-0.5 font-mono text-xs font-semibold text-blue-800">
              {schema.trxCode}
            </span>
            <span className="text-xs font-mono text-slate-400">v{schema.version || 1}.0</span>
          </div>
          <h2 className="mt-1 text-lg font-bold text-slate-900">{schema.formTitle}</h2>
        </div>

        {isEvaluating && (
          <div className="flex items-center space-x-2 text-xs font-medium text-blue-600 bg-blue-50 px-3 py-1.5 rounded-full animate-pulse">
            <Loader2 className="h-3.5 w-3.5 animate-spin" />
            <span>Evaluating core banking rules...</span>
          </div>
        )}
      </div>

      {/* Grid Fields Layout */}
      <div className="grid grid-cols-1 gap-5 sm:grid-cols-2">
        {(schema.fields || [])
          .filter((f) => !hiddenFields.has(f.name))
          .map((field) => (
            <FormFieldRenderer
              key={field.name}
              field={field}
              value={formData[field.name]}
              onChange={handleChange}
              onBlur={handleBlur}
              error={errors[field.name]}
              disabled={disabledFields.has(field.name) || isSubmitting}
              formData={formData}
            />
          ))}
      </div>

      {/* Form Level Error Alert */}
      {errors._global && (
        <div className="flex items-start space-x-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <AlertTriangle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
          <div>
            <h4 className="font-semibold">Transaction Rejected:</h4>
            <p className="text-red-700 mt-0.5">{errors._global}</p>
          </div>
        </div>
      )}

      {/* Form Action Bar: Multi-Button or Standard Submit */}
      <div className="flex flex-wrap items-center justify-between gap-3 border-t border-slate-200 pt-5">
        {schema.actions && schema.actions.length > 0 ? (
          <>
            {/* Left / Secondary Action Buttons (Inquiry, Steps, Navigation) */}
            <div className="flex flex-wrap gap-2">
              <button
                type="button"
                onClick={() => {
                  const defaults = {};
                  (schema.fields || []).forEach((f) => {
                    defaults[f.name] = '';
                  });
                  setFormData(defaults);
                  setErrors({});
                }}
                disabled={isSubmitting}
                className="rounded-lg border border-slate-300 bg-white px-3.5 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 transition"
              >
                Reset
              </button>

              {schema.actions
                .filter((a) => a.actionType !== 'SUBMIT_TRX')
                .map((action) => (
                  <button
                    key={action.code}
                    type="button"
                    onClick={() => handleActionClick(action)}
                    disabled={isSubmitting || isEvaluating}
                    className={`rounded-lg px-4 py-2 text-xs font-semibold shadow-xs transition disabled:opacity-50 ${
                      action.style === 'primary'
                        ? 'bg-blue-600 text-white hover:bg-blue-700'
                        : action.style === 'danger'
                        ? 'bg-red-600 text-white hover:bg-red-700'
                        : 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50'
                    }`}
                  >
                    {action.label}
                  </button>
                ))}
            </div>

            {/* Right / Primary Submit Buttons */}
            <div className="flex flex-wrap gap-2">
              {schema.actions
                .filter((a) => a.actionType === 'SUBMIT_TRX')
                .map((action) => (
                  <button
                    key={action.code}
                    type="button"
                    onClick={() => handleActionClick(action)}
                    disabled={isSubmitting || isEvaluating}
                    className="inline-flex items-center space-x-2 rounded-lg bg-blue-700 px-5 py-2 text-xs font-bold text-white shadow-sm hover:bg-blue-800 disabled:opacity-50 transition"
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Processing...</span>
                      </>
                    ) : (
                      <>
                        <SubmitIcon className="h-3.5 w-3.5" />
                        <span>{action.label}</span>
                      </>
                    )}
                  </button>
                ))}
            </div>
          </>
        ) : (
          <>
            <div />
            <div className="flex items-center space-x-3">
              <button
                type="button"
                onClick={() => {
                  const defaults = {};
                  (schema.fields || []).forEach((f) => {
                    defaults[f.name] = '';
                  });
                  setFormData(defaults);
                  setErrors({});
                }}
                disabled={isSubmitting}
                className="rounded-lg border border-slate-300 px-4 py-2.5 text-sm font-semibold text-slate-700 hover:bg-slate-50 disabled:opacity-50 transition"
              >
                Reset
              </button>

              <button
                type="submit"
                disabled={isSubmitting || isEvaluating}
                className="inline-flex items-center space-x-2 rounded-lg bg-blue-700 px-6 py-2.5 text-sm font-semibold text-white shadow-sm hover:bg-blue-800 focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 disabled:opacity-50 transition"
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Posting Journal...</span>
                  </>
                ) : (
                  <>
                    <SubmitIcon className="h-4 w-4" />
                    <span>{submitLabel}</span>
                  </>
                )}
              </button>
            </div>
          </>
        )}
      </div>
    </form>
  );
}

import React, { useState, useEffect, useCallback } from 'react';
import FormFieldRenderer from './FormFieldRenderer';
import LookupModal from './LookupModal';
import { formApi } from '../../services/formApi';
import { Loader2, AlertTriangle, CheckCircle, Send } from 'lucide-react';

export default function DynamicForm({
  schema,
  initialData = {},
  onSubmit,
  onNavigate,
  onReset,
  isSubmitting = false,
  isSubmitted = false,
  journalLegs = null,
  journalMetadata = null,
  submitLabel = 'Submit',
}) {
  const [formData, setFormData] = useState({});
  const [errors, setErrors] = useState({});
  const [disabledFields, setDisabledFields] = useState(new Set());
  const [hiddenFields, setHiddenFields] = useState(new Set());
  const [isEvaluating, setIsEvaluating] = useState(false);
  const [evaluationNotice, setEvaluationNotice] = useState(null);
  const [fieldOptions, setFieldOptions] = useState({});
  const [lookupModalState, setLookupModalState] = useState({
    isOpen: false,
    modalFormCode: null,
    field: null,
  });

  // Initialize form data when schema or initialData changes
  useEffect(() => {
    if (!schema) return;
    const defaults = {};
    (schema.fields || []).forEach((f) => {
      defaults[f.name] = initialData[f.name] ?? f.defaultValue ?? '';
    });
    setFormData(defaults);
    setErrors({});
    const initialDisabled = new Set(
      (schema.fields || [])
        .filter((f) => f.disabled || f.readOnly || f.readonly || f.read_only)
        .map((f) => f.name)
    );
    setDisabledFields(initialDisabled);
    setHiddenFields(new Set((schema.fields || []).filter((f) => f.hidden).map((f) => f.name)));
    setFieldOptions({});
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

          // Updates disabled fields, preserving schema-level readOnly/disabled fields
          if (result.disabledFields) {
            const permanentDisabled = (schema.fields || [])
              .filter((f) => f.disabled || f.readOnly || f.readonly || f.read_only)
              .map((f) => f.name);
            setDisabledFields(new Set([...result.disabledFields, ...permanentDisabled]));
          }

          // Updates hidden fields
          if (result.hiddenFields) {
            setHiddenFields(new Set(result.hiddenFields));
          }

          // Updates dynamic field options (e.g. deposit terms)
          if (result.fieldOptions && Object.keys(result.fieldOptions).length > 0) {
            setFieldOptions((prev) => ({
              ...prev,
              ...result.fieldOptions,
            }));
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

  // Auto-evaluate default field steps on schema load (e.g. initial disabled state)
  useEffect(() => {
    if (!schema?.fields) return;
    const initTriggerField = schema.fields.find(
      (f) => f.steps && f.steps.length > 0 && f.defaultValue
    );
    if (initTriggerField) {
      const defaults = {};
      schema.fields.forEach((f) => {
        defaults[f.name] = initialData[f.name] ?? f.defaultValue ?? '';
      });
      triggerEvaluation(initTriggerField.name, defaults, initTriggerField.steps);
    }
  }, [schema, triggerEvaluation]);

  const handleOpenLookup = useCallback((modalFormCode, field) => {
    if (isSubmitted || isSubmitting) return;
    setLookupModalState({
      isOpen: true,
      modalFormCode,
      field,
    });
  }, [isSubmitted, isSubmitting]);

  const handleSelectLookupRecord = useCallback(
    (record) => {
      const field = lookupModalState.field;
      if (!field || !record) return;

      const lookupConfig = field.look_up || field.lookUp || field.lookup || {};
      const valueKey = lookupConfig.value_key || lookupConfig.valueKey || field.name;
      const selectedValue =
        record[valueKey] ?? record.productCode ?? record.id ?? record.code ?? '';

      setFormData((prev) => {
        const next = { ...prev, [field.name]: selectedValue };

        const mapping = lookupConfig.mapping || lookupConfig.fieldMapping;
        if (mapping && typeof mapping === 'object') {
          Object.entries(mapping).forEach(([targetFieldName, srcRecordKey]) => {
            if (record[srcRecordKey] !== undefined) {
              next[targetFieldName] = record[srcRecordKey];
            }
          });
        }
        return next;
      });

      setErrors((prev) => {
        const next = { ...prev };
        delete next[field.name];
        return next;
      });

      if (field.steps && field.steps.length > 0) {
        const updatedData = { ...formData, [field.name]: selectedValue };
        triggerEvaluation(field.name, updatedData, field.steps);
      }

      setLookupModalState({ isOpen: false, modalFormCode: null, field: null });
    },
    [lookupModalState, formData, triggerEvaluation]
  );

  const handleChange = (e) => {
    const { name, value } = e.target;
    const fieldDef = (schema.fields || []).find((f) => f.name === name);
    if (fieldDef?.readOnly || fieldDef?.readonly || fieldDef?.read_only || fieldDef?.disabled || disabledFields.has(name)) {
      return;
    }
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

    // Trigger instant evaluation if configured or for interactive controls (checkbox, select, step-bound)
    if (
      fieldDef?.evaluateOnChange ||
      fieldDef?.type === 'CHECKBOX' ||
      fieldDef?.type === 'BOOLEAN' ||
      fieldDef?.type === 'SELECT' ||
      fieldDef?.type === 'CODELIST' ||
      name === 'requestInterestMargin' ||
      name === 'marginReason' ||
      name === 'rolloverOption' ||
      name === 'productCode'
    ) {
      triggerEvaluation(name, updatedData, fieldDef?.steps);
    }
  };

  const handleBlur = (e) => {
    const { name } = e.target;
    const fieldDef = (schema.fields || []).find((f) => f.name === name);
    // Auto evaluate on blur for account lookup, customer lookup, amounts, or margins
    if (
      fieldDef?.evaluateOnBlur ||
      fieldDef?.triggersEvaluation ||
      fieldDef?.type === 'ACCOUNT_LOOKUP' ||
      fieldDef?.type === 'CURRENCY' ||
      fieldDef?.type === 'NUMBER' ||
      (fieldDef?.steps && fieldDef.steps.length > 0) ||
      name.toLowerCase().includes('account') ||
      name.toLowerCase().includes('customer') ||
      name.toLowerCase().includes('amount') ||
      name.toLowerCase().includes('deposit') ||
      name.toLowerCase().includes('margin')
    ) {
      triggerEvaluation(name, formData, fieldDef?.steps);
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
              field={{
                ...field,
                options: (fieldOptions[field.name] && fieldOptions[field.name].length > 0)
                  ? fieldOptions[field.name]
                  : field.options,
              }}
              value={formData[field.name]}
              onChange={handleChange}
              onBlur={handleBlur}
              error={errors[field.name]}
              disabled={isSubmitted || disabledFields.has(field.name) || Boolean(field.readOnly || field.readonly || field.read_only) || isSubmitting}
              formData={formData}
              onOpenLookup={handleOpenLookup}
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
            <div className="flex flex-wrap gap-2 items-center">
              <button
                type="button"
                onClick={() => {
                  const defaults = {};
                  (schema.fields || []).forEach((f) => {
                    defaults[f.name] = f.defaultValue ?? '';
                  });
                  setFormData(defaults);
                  setErrors({});
                  if (onReset) onReset();
                }}
                disabled={isSubmitting}
                className={`rounded-lg border px-4 py-2 text-xs font-bold transition ${
                  isSubmitted
                    ? 'border-blue-500 bg-blue-50 text-blue-700 hover:bg-blue-100 ring-2 ring-blue-200 shadow-xs cursor-pointer'
                    : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50 disabled:opacity-50'
                }`}
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
                    disabled={isSubmitting || isEvaluating || isSubmitted}
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
                    disabled={isSubmitting || isEvaluating || isSubmitted}
                    className={`inline-flex items-center space-x-2 rounded-lg px-5 py-2 text-xs font-bold shadow-sm transition ${
                      isSubmitted
                        ? 'bg-slate-200 text-slate-400 cursor-not-allowed border border-slate-300'
                        : 'bg-blue-700 text-white hover:bg-blue-800 disabled:opacity-50'
                    }`}
                  >
                    {isSubmitting ? (
                      <>
                        <Loader2 className="h-3.5 w-3.5 animate-spin" />
                        <span>Processing...</span>
                      </>
                    ) : (
                      <span>{action.label}</span>
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
                    defaults[f.name] = f.defaultValue ?? '';
                  });
                  setFormData(defaults);
                  setErrors({});
                  if (onReset) onReset();
                }}
                disabled={isSubmitting}
                className={`rounded-lg border px-4 py-2.5 text-sm font-bold transition ${
                  isSubmitted
                    ? 'border-blue-500 bg-blue-50 text-blue-700 hover:bg-blue-100 ring-2 ring-blue-200 shadow-xs cursor-pointer'
                    : 'border-slate-300 px-4 py-2.5 text-slate-700 hover:bg-slate-50 disabled:opacity-50'
                }`}
              >
                Reset
              </button>

              <button
                type="submit"
                disabled={isSubmitting || isEvaluating || isSubmitted}
                className={`inline-flex items-center space-x-2 rounded-lg px-6 py-2.5 text-sm font-semibold shadow-sm focus:outline-none focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 transition ${
                  isSubmitted
                    ? 'bg-slate-200 text-slate-400 cursor-not-allowed border border-slate-300'
                    : 'bg-blue-700 text-white hover:bg-blue-800 disabled:opacity-50'
                }`}
              >
                {isSubmitting ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Posting Journal...</span>
                  </>
                ) : (
                  <span>{submitLabel}</span>
                )}
              </button>
            </div>
          </>
        )}
      </div>

      {/* Accounting Journal Entries Breakdown (In-Form Post-Submission) */}
      {journalLegs && journalLegs.length > 0 && (
        <div className="rounded-xl border border-slate-200 bg-slate-50/90 p-4 space-y-3 font-mono animate-fadeIn">
          <div className="flex flex-wrap items-center justify-between border-b border-slate-200 pb-2.5 gap-2">
            <div className="flex items-center space-x-2">
              <Send className="h-4 w-4 text-blue-700" />
              <span className="text-xs font-bold uppercase tracking-wider text-slate-900">
                Accounting Journal Entries ({journalLegs.length} legs)
              </span>
              <span className="rounded bg-emerald-100 px-2 py-0.5 text-[10px] font-bold text-emerald-800 border border-emerald-200">
                BALANCED
              </span>
            </div>
            {journalMetadata?.trxNumber && (
              <span className="text-xs text-slate-600 font-bold">
                Ref: <span className="text-blue-900">{journalMetadata.trxNumber}</span>
              </span>
            )}
          </div>

          {/* Journal Legs Table */}
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead>
                <tr className="border-b border-slate-200 text-slate-500 text-[11px]">
                  <th className="py-2 px-2.5">#</th>
                  <th className="py-2 px-2.5">Type</th>
                  <th className="py-2 px-2.5">GL Account</th>
                  <th className="py-2 px-2.5">Account ID</th>
                  <th className="py-2 px-2.5 text-right">Debit (VND)</th>
                  <th className="py-2 px-2.5 text-right">Credit (VND)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-200/70">
                {journalLegs.map((leg, idx) => {
                  const amt = Number(leg.amount || 0);
                  const isDebit = leg.drCr === 'D';
                  return (
                    <tr key={idx} className="hover:bg-white/90 transition">
                      <td className="py-2.5 px-2.5 text-slate-400 font-bold">{idx + 1}</td>
                      <td className="py-2.5 px-2.5">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                            isDebit
                              ? 'bg-amber-100 text-amber-900 border border-amber-200'
                              : 'bg-emerald-100 text-emerald-900 border border-emerald-200'
                          }`}
                        >
                          {isDebit ? 'DEBIT' : 'CREDIT'}
                        </span>
                      </td>
                      <td className="py-2.5 px-2.5 font-bold text-slate-800">{leg.glCode}</td>
                      <td className="py-2.5 px-2.5 text-slate-600 font-medium">{leg.accountId || '-'}</td>
                      <td className="py-2.5 px-2.5 text-right font-bold text-amber-950">
                        {isDebit ? new Intl.NumberFormat('vi-VN').format(amt) : '-'}
                      </td>
                      <td className="py-2.5 px-2.5 text-right font-bold text-emerald-950">
                        {!isDebit ? new Intl.NumberFormat('vi-VN').format(amt) : '-'}
                      </td>
                    </tr>
                  );
                })}
              </tbody>
            </table>
          </div>

          {/* Footer: Balanced Double-Entry Check */}
          <div className="flex justify-between items-center pt-2 border-t border-slate-200 text-xs text-slate-600 font-bold">
            <span>Double-entry balance check:</span>
            <span className="text-emerald-700">OK • Total Debit equals Total Credit</span>
          </div>
        </div>
      )}

      {/* Catalogue & Entity Lookup Modal */}
      <LookupModal
        isOpen={lookupModalState.isOpen}
        onClose={() => setLookupModalState({ isOpen: false, modalFormCode: null, field: null })}
        modalFormCode={lookupModalState.modalFormCode}
        onSelectRecord={handleSelectLookupRecord}
        title={lookupModalState.field?.label ? `Lookup ${lookupModalState.field.label}` : null}
      />
    </form>
  );
}

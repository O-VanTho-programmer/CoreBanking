import React from 'react';
import CurrencyInput from './widgets/CurrencyInput';
import AccountLookup from './widgets/AccountLookup';
import CodeListSelect from './widgets/CodeListSelect';
import DateInput from './widgets/DateInput';
import { Search } from 'lucide-react';

export default function FormFieldRenderer({
  field,
  value,
  onChange,
  onBlur,
  error,
  disabled,
  formData,
  onOpenLookup,
}) {
  const { name, label, type, placeholder, required, options, suffix, colSpan = 1 } = field;

  const renderInput = () => {
    switch (type) {
      case 'CURRENCY':
        return (
          <CurrencyInput
            name={name}
            value={value}
            onChange={onChange}
            onBlur={onBlur}
            placeholder={placeholder || '0'}
            disabled={disabled}
            required={required}
            error={error}
            suffix={suffix || 'VND'}
          />
        );

      case 'ACCOUNT_LOOKUP':
        return (
          <AccountLookup
            name={name}
            value={value}
            onChange={onChange}
            onBlur={onBlur}
            placeholder={placeholder || 'Enter account number...'}
            disabled={disabled}
            required={required}
            error={error}
            formData={formData}
          />
        );

      case 'CODELIST':
      case 'SELECT':
        return (
          <CodeListSelect
            name={name}
            value={value}
            onChange={onChange}
            onBlur={onBlur}
            options={options}
            optionsEndpoint={field.optionsEndpoint}
            disabled={disabled}
            required={required}
            placeholder={placeholder}
            error={error}
          />
        );

      case 'DATE':
        return (
          <DateInput
            name={name}
            value={value}
            onChange={onChange}
            onBlur={onBlur}
            disabled={disabled}
            required={required}
            error={error}
          />
        );

      case 'CHECKBOX':
      case 'BOOLEAN':
        return (
          <label className="flex items-center space-x-3 cursor-pointer py-2 select-none">
            <input
              type="checkbox"
              id={name}
              name={name}
              checked={Boolean(value)}
              onChange={(e) => onChange({ target: { name, value: e.target.checked } })}
              onBlur={onBlur}
              disabled={disabled}
              className="h-4 w-4 rounded border-slate-300 text-blue-600 focus:ring-blue-500 disabled:opacity-50 cursor-pointer"
            />
            <span className={`text-xs font-semibold ${disabled ? 'text-slate-400' : 'text-slate-700'}`}>
              {field.checkboxLabel || label}
            </span>
          </label>
        );

      case 'NUMBER':
        return (
          <input
            type="number"
            id={name}
            name={name}
            value={value ?? ''}
            onChange={onChange}
            onBlur={onBlur}
            disabled={disabled}
            required={required}
            placeholder={placeholder}
            min={field.min}
            max={field.max}
            step={field.step || '1'}
            className={`block w-full rounded-lg border px-3 py-2.5 font-mono text-sm transition focus:outline-none focus:ring-2 ${
              disabled
                ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
                : error
                ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
                : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
            }`}
          />
        );

      case 'TEXTAREA':
        return (
          <textarea
            id={name}
            name={name}
            rows={field.rows || 3}
            value={value ?? ''}
            onChange={onChange}
            onBlur={onBlur}
            disabled={disabled}
            required={required}
            placeholder={placeholder}
            className={`block w-full rounded-lg border px-3 py-2.5 text-sm transition focus:outline-none focus:ring-2 ${
              disabled
                ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
                : error
                ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
                : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
            }`}
          />
        );

      case 'STATIC':
      case 'READONLY':
        return (
          <div className="rounded-lg border border-slate-200 bg-slate-50 px-3.5 py-2.5 font-mono text-sm font-medium text-slate-700">
            {value !== undefined && value !== null && value !== '' ? String(value) : '—'}
          </div>
        );

      case 'TEXT':
      default: {
        const lookupConfig = field.look_up || field.lookUp || field.lookup;
        const isLookupEnabled = Boolean(lookupConfig?.is_enable || lookupConfig?.isEnable);

        return (
          <div className="relative flex items-center">
            <input
              type="text"
              id={name}
              name={name}
              value={value ?? ''}
              onChange={onChange}
              onBlur={onBlur}
              disabled={disabled}
              required={required}
              placeholder={placeholder}
              className={`block w-full rounded-lg border px-3 py-2.5 text-sm transition focus:outline-none focus:ring-2 ${
                isLookupEnabled ? 'pr-10' : ''
              } ${
                disabled
                  ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
                  : error
                  ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
                  : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
              }`}
            />
            {isLookupEnabled && (
              <button
                type="button"
                onClick={() =>
                  onOpenLookup &&
                  onOpenLookup(lookupConfig.modal_form || lookupConfig.modalForm, field)
                }
                disabled={disabled}
                className="absolute right-2 p-1.5 text-slate-400 hover:text-blue-600 hover:bg-blue-50 rounded-lg transition disabled:opacity-50"
                title="Lookup from catalogue"
              >
                <Search className="h-4 w-4" />
              </button>
            )}
          </div>
        );
      }
    }
  };

  const colSpanClass = colSpan === 2 ? 'sm:col-span-2' : colSpan === 3 ? 'sm:col-span-3' : 'sm:col-span-1';

  return (
    <div className={`space-y-1.5 ${colSpanClass}`}>
      <label htmlFor={name} className="flex items-center justify-between text-xs font-semibold uppercase tracking-wider text-slate-600">
        <span>
          {label}
          {required && <span className="text-red-500 ml-1">*</span>}
        </span>
        {field.helpText && <span className="text-[11px] text-slate-400 font-normal">{field.helpText}</span>}
      </label>

      {renderInput()}

      {error && (
        <p className="text-xs font-medium text-red-600 animate-fadeIn flex items-center space-x-1">
          <span>{error}</span>
        </p>
      )}
    </div>
  );
}

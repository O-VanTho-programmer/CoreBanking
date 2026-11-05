import React from 'react';
import CurrencyInput from './widgets/CurrencyInput';
import AccountLookup from './widgets/AccountLookup';
import CodeListSelect from './widgets/CodeListSelect';
import DateInput from './widgets/DateInput';

export default function FormFieldRenderer({
  field,
  value,
  onChange,
  onBlur,
  error,
  disabled,
  formData,
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
            placeholder={placeholder || 'Nhập số tài khoản...'}
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
      default:
        return (
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
              disabled
                ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
                : error
                ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
                : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
            }`}
          />
        );
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

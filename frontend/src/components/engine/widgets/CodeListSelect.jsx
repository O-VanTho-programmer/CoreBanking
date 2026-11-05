import React from 'react';

export default function CodeListSelect({
  id,
  name,
  value,
  onChange,
  onBlur,
  options = [],
  disabled = false,
  required = false,
  placeholder = '-- Chọn --',
  error,
}) {
  return (
    <select
      id={id || name}
      name={name}
      value={value || ''}
      onChange={onChange}
      onBlur={onBlur}
      disabled={disabled}
      required={required}
      className={`block w-full rounded-lg border px-3 py-2.5 text-sm font-medium transition focus:outline-none focus:ring-2 ${
        disabled
          ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
          : error
          ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
          : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
      }`}
    >
      <option value="">{placeholder}</option>
      {options.map((opt) => {
        const optVal = typeof opt === 'object' ? opt.value : opt;
        const optLabel = typeof opt === 'object' ? opt.label : opt;
        return (
          <option key={optVal} value={optVal}>
            {optLabel}
          </option>
        );
      })}
    </select>
  );
}

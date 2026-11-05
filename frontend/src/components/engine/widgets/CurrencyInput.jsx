import React from 'react';

export default function CurrencyInput({
  id,
  name,
  value,
  onChange,
  onBlur,
  placeholder = '0',
  disabled = false,
  required = false,
  error,
  suffix = 'VND',
}) {
  const formatDisplay = (val) => {
    if (val === undefined || val === null || val === '') return '';
    const num = Number(val);
    if (isNaN(num)) return val;
    return new Intl.NumberFormat('vi-VN').format(num);
  };

  const handleChange = (e) => {
    // Strip non-numeric characters except decimal dot if needed
    const raw = e.target.value.replace(/[^0-9]/g, '');
    const num = raw === '' ? '' : Number(raw);
    onChange({
      target: {
        name,
        value: num,
      },
    });
  };

  return (
    <div className="relative rounded-md shadow-sm">
      <input
        type="text"
        id={id || name}
        name={name}
        disabled={disabled}
        required={required}
        value={formatDisplay(value)}
        onChange={handleChange}
        onBlur={onBlur}
        placeholder={placeholder}
        className={`block w-full rounded-lg border px-3 py-2.5 pr-14 text-right font-mono text-base font-semibold transition focus:outline-none focus:ring-2 ${
          disabled
            ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
            : error
            ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
            : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
        }`}
      />
      <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-3">
        <span className="text-xs font-semibold text-slate-400 uppercase tracking-wider">{suffix}</span>
      </div>
    </div>
  );
}

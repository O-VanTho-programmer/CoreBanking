import React from 'react';
import { Calendar } from 'lucide-react';

export default function DateInput({
  id,
  name,
  value,
  onChange,
  onBlur,
  disabled = false,
  required = false,
  error,
}) {
  return (
    <div className="relative rounded-md shadow-sm">
      <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
        <Calendar className="h-4 w-4 text-slate-400" />
      </div>
      <input
        type="date"
        id={id || name}
        name={name}
        value={value || ''}
        onChange={onChange}
        onBlur={onBlur}
        disabled={disabled}
        required={required}
        className={`block w-full rounded-lg border py-2.5 pl-10 pr-3 font-mono text-sm transition focus:outline-none focus:ring-2 ${
          disabled
            ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
            : error
            ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
            : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
        }`}
      />
    </div>
  );
}

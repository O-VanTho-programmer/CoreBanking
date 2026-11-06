import React from 'react';
import { CreditCard, CheckCircle2, AlertCircle, ShieldCheck } from 'lucide-react';

export default function AccountLookup({
  id,
  name,
  value,
  onChange,
  onBlur,
  placeholder = 'Enter 16-digit account number...',
  disabled = false,
  required = false,
  error,
  formData = {},
  prefix = '',
}) {
  // Infer companion fields by convention:
  // e.g. for sourceAccountNumber -> sourceAccountName, sourceAccountBalance
  // for destAccountNumber -> destAccountName, destAccountBalance
  // or default fallback -> accountHolderName, availableBalance
  const holderName =
    formData[`${name}HolderName`] ||
    formData[`${name}Name`] ||
    (name.includes('source') ? formData.sourceAccountName : null) ||
    (name.includes('dest') ? formData.destAccountName : null) ||
    formData.accountHolderName ||
    formData.customerName;

  const availableBal =
    formData[`${name}AvailableBalance`] ||
    (name.includes('source') ? formData.sourceAvailableBalance : null) ||
    formData.availableBalance;

  const currency = formData.currency || 'VND';

  const formatMoney = (amount) => {
    if (amount === undefined || amount === null) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(amount));
  };

  return (
    <div className="space-y-2">
      <div className="relative rounded-md shadow-sm">
        <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
          <CreditCard className="h-5 w-5 text-slate-400" />
        </div>
        <input
          type="text"
          id={id || name}
          name={name}
          maxLength={20}
          disabled={disabled}
          required={required}
          value={value || ''}
          onChange={onChange}
          onBlur={onBlur}
          placeholder={placeholder}
          className={`block w-full rounded-lg border py-2.5 pl-10 pr-3 font-mono text-sm tracking-wide transition focus:outline-none focus:ring-2 ${
            disabled
              ? 'bg-slate-100 text-slate-500 border-slate-300 cursor-not-allowed'
              : error
              ? 'border-red-400 bg-red-50 text-red-900 focus:border-red-500 focus:ring-red-200'
              : 'border-slate-300 bg-white text-slate-900 focus:border-blue-600 focus:ring-blue-100'
          }`}
        />
      </div>

      {/* Account Info Badge when resolved */}
      {holderName && (
        <div className="flex items-center justify-between rounded-lg border border-emerald-200 bg-emerald-50/70 px-3.5 py-2 text-xs text-emerald-900 animate-fadeIn">
          <div className="flex items-center space-x-2">
            <CheckCircle2 className="h-4 w-4 text-emerald-600 shrink-0" />
            <span className="font-semibold uppercase tracking-wider text-emerald-800">
              {holderName}
            </span>
            <span className="rounded bg-emerald-200/80 px-1.5 py-0.5 text-[10px] font-bold text-emerald-800">
              ACTIVE
            </span>
          </div>
          {availableBal !== undefined && availableBal !== null && (
            <div className="text-right">
              <span className="text-emerald-750 font-normal">Available: </span>
              <span className="font-mono font-bold text-emerald-900">
                {formatMoney(availableBal)} {currency}
              </span>
            </div>
          )}
        </div>
      )}
    </div>
  );
}

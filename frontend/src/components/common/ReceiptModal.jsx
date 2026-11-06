import React from 'react';
import { CheckCircle2, Printer, X, ShieldCheck } from 'lucide-react';

export default function ReceiptModal({ isOpen, onClose, receipt }) {
  if (!isOpen || !receipt) return null;

  const formatMoney = (amount) => {
    if (amount === undefined || amount === null) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(amount));
  };

  const handlePrint = () => {
    window.print();
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-slate-900/60 p-4 backdrop-blur-sm animate-fadeIn">
      <div className="relative w-full max-w-lg rounded-2xl bg-white shadow-2xl overflow-hidden border border-slate-200">
        {/* Header Ribbon */}
        <div className="bg-gradient-to-r from-blue-900 to-indigo-900 px-6 py-5 text-white">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2">
              <ShieldCheck className="h-6 w-6 text-blue-300" />
              <h3 className="text-base font-bold tracking-wide">ELECTRONIC TRANSACTION RECEIPT</h3>
            </div>
            <button
              onClick={onClose}
              className="rounded-lg p-1 text-blue-200 hover:bg-white/10 hover:text-white transition"
            >
              <X className="h-5 w-5" />
            </button>
          </div>
          <p className="mt-1 text-xs text-blue-200">
            Core Banking System • Real-Time Transaction Engine
          </p>
        </div>

        {/* Receipt Body */}
        <div className="p-6 space-y-5 print:p-0">
          {/* Status Badge */}
          <div className="flex items-center justify-center space-x-2 rounded-xl bg-emerald-50 py-3 text-emerald-800 border border-emerald-200">
            <CheckCircle2 className="h-5 w-5 text-emerald-600" />
            <span className="font-bold text-sm tracking-wide">
              {receipt.status || 'TRANSACTION SUCCESS (POSTED)'}
            </span>
          </div>

          {/* Reference Info */}
          <div className="space-y-2.5 rounded-xl bg-slate-50 p-4 border border-slate-200/80 text-xs">
            <div className="flex justify-between">
              <span className="text-slate-500">Transaction Ref (Journal Trx):</span>
              <span className="font-mono font-bold text-slate-900">{receipt.trxId || receipt.transactionId || 'TRX-CBS'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Journal Entry ID:</span>
              <span className="font-mono font-semibold text-slate-800">{receipt.journalId || receipt.referenceNumber || 'N/A'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Transaction Type:</span>
              <span className="font-semibold text-blue-900">{receipt.trxType || receipt.transactionType || 'TRX'}</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Posting Timestamp:</span>
              <span className="font-mono text-slate-700">{receipt.timestamp || new Date().toISOString()}</span>
            </div>
          </div>

          {/* Amount Box */}
          <div className="rounded-xl border border-blue-100 bg-blue-50/50 p-4 text-center">
            <span className="text-xs font-semibold text-blue-700 uppercase tracking-wider">
              Transaction Amount
            </span>
            <div className="mt-1 font-mono text-2xl font-black text-blue-950">
              {formatMoney(receipt.amount)} <span className="text-sm font-semibold text-blue-700">{receipt.currency || 'VND'}</span>
            </div>
            {receipt.feeAmount !== undefined && receipt.feeAmount > 0 && (
              <p className="mt-1 text-xs text-slate-500 font-mono">
                Transaction Fee: +{formatMoney(receipt.feeAmount)} {receipt.currency || 'VND'}
              </p>
            )}
          </div>

          {/* Account Breakdown */}
          <div className="space-y-2 text-xs">
            {receipt.sourceAccountNumber && (
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Debit Account:</span>
                <span className="font-mono font-semibold text-slate-900">{receipt.sourceAccountNumber}</span>
              </div>
            )}
            {receipt.destAccountNumber && (
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Credit Account:</span>
                <span className="font-mono font-semibold text-slate-900">{receipt.destAccountNumber}</span>
              </div>
            )}
            {receipt.accountNumber && (
              <div className="flex justify-between border-b border-slate-100 pb-2">
                <span className="text-slate-500">Customer Account:</span>
                <span className="font-mono font-semibold text-slate-900">{receipt.accountNumber}</span>
              </div>
            )}
            {receipt.description && (
              <div className="flex justify-between pt-1">
                <span className="text-slate-500">Description:</span>
                <span className="text-slate-800 font-medium text-right max-w-xs">{receipt.description}</span>
              </div>
            )}
          </div>
        </div>

        {/* Footer Actions */}
        <div className="flex items-center justify-between border-t border-slate-200 bg-slate-50 px-6 py-4">
          <button
            onClick={handlePrint}
            type="button"
            className="inline-flex items-center space-x-2 rounded-lg border border-slate-300 bg-white px-4 py-2 text-xs font-semibold text-slate-700 hover:bg-slate-100 transition shadow-sm"
          >
            <Printer className="h-4 w-4" />
            <span>Print Receipt</span>
          </button>

          <button
            onClick={onClose}
            type="button"
            className="rounded-lg bg-blue-700 px-5 py-2 text-xs font-semibold text-white hover:bg-blue-800 transition shadow-sm"
          >
            Close
          </button>
        </div>
      </div>
    </div>
  );
}

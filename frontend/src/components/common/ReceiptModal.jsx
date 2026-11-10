import React from 'react';
import { Printer, X } from 'lucide-react';

export default function ReceiptModal({ isOpen, onClose, receipt }) {
  if (!isOpen || !receipt) return null;

  const formatMoney = (amount) => {
    if (amount === undefined || amount === null || isNaN(Number(amount))) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(amount));
  };

  const maskAccountNumber = (accNo) => {
    if (!accNo) return 'N/A';
    const str = String(accNo).trim();
    if (str.length === 16) {
      return `${str.slice(0, 4)} **** **** ${str.slice(12)}`;
    }
    if (str.length > 8) {
      return `${str.slice(0, 4)} **** ${str.slice(-4)}`;
    }
    return str;
  };

  const formatTimestamp = (ts) => {
    if (!ts) return new Date().toLocaleString('sv-SE').replace('T', ' ');
    try {
      const d = new Date(ts);
      return d.toLocaleString('vi-VN', {
        year: 'numeric',
        month: '2-digit',
        day: '2-digit',
        hour: '2-digit',
        minute: '2-digit',
        second: '2-digit',
        hour12: false,
      });
    } catch {
      return String(ts);
    }
  };

  const handlePrint = () => {
    window.print();
  };

  const amountVal = receipt.amount ?? receipt.totalAmount ?? 0;
  const currencyVal = receipt.currency || 'VND';
  const statusVal = (receipt.status || 'SUCCESS').toUpperCase();
  const isSuccess = statusVal === 'SUCCESS' || statusVal === 'COMPLETED';

  const balanceBefore = receipt.balanceBefore !== undefined && receipt.balanceBefore !== null
    ? formatMoney(receipt.balanceBefore) + ' ' + currencyVal
    : 'N/A';

  const balanceAfter = receipt.balanceAfter !== undefined && receipt.balanceAfter !== null
    ? formatMoney(receipt.balanceAfter) + ' ' + currencyVal
    : (receipt.toAccountAvailableBalance !== undefined && receipt.toAccountAvailableBalance !== null
      ? formatMoney(receipt.toAccountAvailableBalance) + ' ' + currencyVal
      : 'N/A');

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-fadeIn">
      <div className="relative w-full max-w-lg rounded-none bg-white text-black shadow-2xl border-2 border-black overflow-hidden font-mono">
        {/* Top Close Bar */}
        <div className="flex justify-between items-center border-b-2 border-black bg-white px-5 py-2.5">
          <span className="text-xs font-bold uppercase tracking-widest text-black">
            OFFICIAL BANK VOUCHER
          </span>
          <button
            onClick={onClose}
            className="p-1 text-black hover:bg-black hover:text-white transition rounded-none"
            aria-label="Close"
          >
            <X className="h-4 w-4" />
          </button>
        </div>

        {/* Printable Receipt Area */}
        <div className="p-6 space-y-4 bg-white text-black print:p-0">
          {/* Header */}
          <div className="text-center space-y-1 border-b-2 border-dashed border-black pb-4">
            <h2 className="text-lg font-black tracking-wider text-black">CORE BANKING SYSTEM</h2>
            <p className="text-xs font-bold uppercase tracking-widest text-black">
              DEPOSIT RECEIPT
            </p>
            <p className="text-[11px] text-black">Branch: VN001 • Counter Transaction Advice</p>
          </div>

          {/* Status Badge */}
          <div className="flex justify-center">
            <div className={`px-4 py-1 border-2 border-black text-xs font-black tracking-widest ${isSuccess ? 'bg-black text-white' : 'bg-white text-black'}`}>
              [ STATUS: {isSuccess ? 'SUCCESS' : 'FAILED'} ]
            </div>
          </div>

          {/* Transaction Metadata */}
          <div className="space-y-1.5 border-b border-dashed border-black pb-3 text-xs leading-relaxed">
            <div className="flex justify-between">
              <span className="font-bold text-black">Transaction ID:</span>
              <span className="font-bold text-black text-right">{receipt.trxNumber || receipt.trxId || 'N/A'}</span>
            </div>
            {receipt.journalId && (
              <div className="flex justify-between">
                <span className="font-bold text-black">Journal Entry ID:</span>
                <span className="text-black text-right">
                  {receipt.journalId}{receipt.journalEntryIds?.length > 1 ? ` (${receipt.journalEntryIds.join(', ')})` : ''}
                </span>
              </div>
            )}
            <div className="flex justify-between">
              <span className="font-bold text-black">Timestamp:</span>
              <span className="text-black text-right">{formatTimestamp(receipt.timestamp)}</span>
            </div>
            <div className="flex justify-between">
              <span className="font-bold text-black">Transaction Type:</span>
              <span className="font-bold text-black text-right">{receipt.trxType || 'Cash'}</span>
            </div>
            <div className="flex justify-between">
              <span className="font-bold text-black">Account Number:</span>
              <span className="font-bold text-black text-right tracking-wider">
                {maskAccountNumber(receipt.accountNumber || receipt.toAccountNumber || receipt.destAccountNumber)}
              </span>
            </div>
            <div className="flex justify-between">
              <span className="font-bold text-black">Account Holder Name:</span>
              <span className="font-bold text-black text-right uppercase">
                {receipt.accountHolderName || receipt.customerName || 'N/A'}
              </span>
            </div>
          </div>

          {/* Amount Box */}
          <div className="flex justify-between">
            <span className="font-bold text-black">Transaction Amount:</span>
            <span className="text-black font-bold">{formatMoney(amountVal)} {currencyVal}</span>
          </div>
          {receipt.feeAmount !== undefined && Number(receipt.feeAmount) > 0 && (
            <div className="text-[11px] text-black">
              Fee: +{formatMoney(receipt.feeAmount)} {currencyVal}
            </div>
          )}

          {/* Balance Before & After */}
          <div className="space-y-1.5 border-b border-dashed border-black pb-3 text-xs">
            <div className="flex justify-between">
              <span className="font-bold text-black">Balance Before:</span>
              <span className="text-black font-medium">{balanceBefore}</span>
            </div>
            <div className="flex justify-between">
              <span className="font-bold text-black">Balance After:</span>
              <span className="text-black font-bold">{balanceAfter}</span>
            </div>
          </div>

          {/* Narration */}
          <div className="border-b border-dashed border-black pb-3 text-xs space-y-0.5">
            <span className="font-bold text-black block">Narration:</span>
            <p className="text-black break-words italic pl-2">
              {receipt.narration || receipt.description || 'Counter cash deposit'}
            </p>
          </div>

          {/* Accounting Journal Legs */}
          {receipt.legs && receipt.legs.length > 0 && (
            <div className="border-b border-dashed border-black pb-3 text-[11px] space-y-1">
              <span className="font-bold text-black block uppercase tracking-wider">
                Accounting Journal Legs ({receipt.legs.length})
              </span>
              <div className="space-y-1">
                {receipt.legs.map((leg, idx) => (
                  <div key={idx} className="flex justify-between items-center text-black">
                    <span>
                      [{leg.drCr === 'D' ? 'DR' : 'CR'}] GL {leg.glCode} {leg.accountId ? `(Acc: ${leg.accountId})` : ''}
                    </span>
                    <span className="font-bold">
                      {formatMoney(leg.amount)} {leg.currency || currencyVal}
                    </span>
                  </div>
                ))}
              </div>
            </div>
          )}

          {/* Receipt Footer */}
          <div className="text-center text-[10px] text-black pt-3 border-t-2 border-dashed border-black space-y-0.5">
            <p className="font-bold">*** PLEASE RETAIN THIS RECEIPT FOR YOUR RECORDS ***</p>
            <p>Thank you for banking with us!</p>
          </div>
        </div>

        {/* Modal Action Buttons (Black and White) */}
        <div className="flex items-center justify-between border-t-2 border-black bg-white px-6 py-3">
          <button
            onClick={handlePrint}
            type="button"
            className="inline-flex items-center space-x-2 border-2 border-black bg-white px-4 py-2 text-xs font-bold text-black hover:bg-black hover:text-white transition rounded-none"
          >
            <Printer className="h-4 w-4" />
            <span>PRINT RECEIPT</span>
          </button>

          <button
            onClick={onClose}
            type="button"
            className="border-2 border-black bg-black px-6 py-2 text-xs font-bold text-white hover:bg-neutral-800 transition rounded-none"
          >
            CLOSE
          </button>
        </div>
      </div>
    </div>
  );
}

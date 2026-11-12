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

  const rawType = (receipt.trxType || receipt.trxCode || receipt.voucherKind || '').toUpperCase();
  const isWithdraw = receipt.voucherKind === 'WITHDRAWAL' || rawType.includes('WDR') || rawType.includes('WITHDRAW');
  const isTransfer = receipt.voucherKind === 'TRANSFER' || rawType.includes('TRF') || rawType.includes('TRANSFER');
  const isLoan = receipt.voucherKind === 'LOAN' || rawType.includes('LOAN') || rawType.includes('DISB');
  const isAccountOpen = receipt.voucherKind === 'ACCOUNT_OPEN' || rawType.includes('ACCOUNT_OPEN');
  const isDeposit = !isWithdraw && !isTransfer && !isLoan && !isAccountOpen;

  // Determine Title & Subtitle based on transaction kind
  let voucherTitle = 'DEPOSIT RECEIPT';
  let voucherSubtitle = 'Branch: VN001 • Counter Cash Deposit Advice';
  let displayTrxType = receipt.trxType || 'Cash Deposit';

  if (isWithdraw) {
    voucherTitle = 'WITHDRAWAL RECEIPT';
    voucherSubtitle = 'Branch: VN001 • Counter Cash Withdrawal Advice';
    displayTrxType = receipt.trxType || 'Cash Withdrawal';
  } else if (isTransfer) {
    voucherTitle = 'FUND TRANSFER ADVICE';
    voucherSubtitle = 'Branch: VN001 • Internal Account Transfer Advice';
    displayTrxType = receipt.trxType || 'Internal Transfer';
  } else if (isLoan) {
    voucherTitle = 'LOAN DISBURSEMENT ADVICE';
    voucherSubtitle = 'Branch: VN001 • Credit Facility Disbursement Advice';
    displayTrxType = receipt.trxType || 'Loan Disbursement';
  } else if (isAccountOpen) {
    voucherTitle = 'ACCOUNT OPENING ADVICE';
    voucherSubtitle = 'Branch: VN001 • Deposit Account Creation Voucher';
    displayTrxType = receipt.trxType || 'Account Opening';
  }

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
    <div
      className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 p-4 backdrop-blur-xs animate-fadeIn"
      style={{ margin: 0 }}
    >
      <div className="relative w-full max-w-lg rounded-none bg-white text-black shadow-2xl border-2 border-black overflow-y-auto font-mono">
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
              {voucherTitle}
            </p>
            <p className="text-[11px] text-black">{voucherSubtitle}</p>
          </div>

          <div className="flex justify-between items-center">
            <span className="text-xs font-black tracking-widest">
              STATUS: {isSuccess ? 'SUCCESS' : 'FAILED'}
            </span>
            <span className="text-[11px] text-black">
              Channel: {receipt.channelId || 'BRANCH'}
            </span>
          </div>

          {/* Transaction Metadata Section */}
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
              <span className="font-bold text-black text-right">{displayTrxType}</span>
            </div>

            {/* Account Information - Dynamically structured */}
            {isTransfer ? (
              <>
                <div className="flex justify-between">
                  <span className="font-bold text-black">Ordering Account (Debit):</span>
                  <span className="font-bold text-black text-right tracking-wider">
                    {maskAccountNumber(receipt.sourceAccountNumber || receipt.fromAccountNumber)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-bold text-black">Beneficiary Account (Credit):</span>
                  <span className="font-bold text-black text-right tracking-wider">
                    {maskAccountNumber(receipt.destAccountNumber || receipt.toAccountNumber)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-bold text-black">Beneficiary Name:</span>
                  <span className="font-bold text-black text-right uppercase">
                    {receipt.accountHolderName || receipt.destAccountHolderName || 'N/A'}
                  </span>
                </div>
              </>
            ) : isWithdraw ? (
              <>
                <div className="flex justify-between">
                  <span className="font-bold text-black">Debited Account:</span>
                  <span className="font-bold text-black text-right tracking-wider">
                    {maskAccountNumber(receipt.accountNumber || receipt.fromAccountNumber)}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="font-bold text-black">Account Holder Name:</span>
                  <span className="font-bold text-black text-right uppercase">
                    {receipt.accountHolderName || receipt.customerName || 'N/A'}
                  </span>
                </div>
              </>
            ) : (
              <>
                <div className="flex justify-between">
                  <span className="font-bold text-black">
                    {isAccountOpen ? 'New Account Number:' : isLoan ? 'Disbursement Account:' : 'Beneficiary Account:'}
                  </span>
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
              </>
            )}
          </div>

          {/* Amount Box */}
          <div className="flex justify-between items-center py-1">
            <span className="font-bold text-black text-sm">Transaction Amount:</span>
            <span className="text-black font-black text-base">{formatMoney(amountVal)} {currencyVal}</span>
          </div>
          {receipt.feeAmount !== undefined && Number(receipt.feeAmount) > 0 && (
            <div className="flex justify-between text-[11px] text-black">
              <span>Transfer Fee ({receipt.feePayer || 'SOURCE'}):</span>
              <span className="font-bold">+{formatMoney(receipt.feeAmount)} {currencyVal}</span>
            </div>
          )}

          {/* Balance Before & After (Not applicable to pure registration) */}
          {(balanceBefore !== 'N/A' || balanceAfter !== 'N/A') && (
            <div className="space-y-1.5 border-b border-dashed border-black pb-3 text-xs">
              <div className="flex justify-between">
                <span className="font-bold text-black">
                  {isTransfer ? 'Ordering Balance Before:' : 'Balance Before:'}
                </span>
                <span className="text-black font-medium">{balanceBefore}</span>
              </div>
              <div className="flex justify-between">
                <span className="font-bold text-black">
                  {isTransfer ? 'Ordering Balance After:' : 'Balance After:'}
                </span>
                <span className="text-black font-bold">{balanceAfter}</span>
              </div>
            </div>
          )}

          {/* Narration */}
          <div className="border-b border-dashed border-black pb-3 text-xs space-y-0.5">
            <span className="font-bold text-black block">Narration:</span>
            <p className="text-black break-words italic pl-2">
              {receipt.narration || receipt.description || 'Counter transaction'}
            </p>
          </div>

          {/* Receipt Footer Notice */}
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
            className="inline-flex items-center space-x-2 border-2 border-black bg-white px-4 py-2 text-xs font-bold text-black hover:bg-black hover:text-white transition rounded-none cursor-pointer"
          >
            <Printer className="h-4 w-4" />
            <span>PRINT RECEIPT</span>
          </button>

          <button
            onClick={onClose}
            type="button"
            className="border-2 border-black bg-black px-6 py-2 text-xs font-bold text-white hover:bg-neutral-800 transition rounded-none cursor-pointer"
          >
            CLOSE
          </button>
        </div>
      </div>
    </div>
  );
}

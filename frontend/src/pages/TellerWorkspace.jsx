import React, { useState, useEffect } from 'react';
import DynamicForm from '../components/engine/DynamicForm';
import ReceiptModal from '../components/common/ReceiptModal';
import { formApi } from '../services/formApi';
import { transactionApi } from '../services/transactionApi';
import { accountApi } from '../services/accountApi';
import {
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  Search,
  CheckCircle2,
  AlertCircle,
  Loader2,
  CreditCard,
  Building,
} from 'lucide-react';

export default function TellerWorkspace({ onTransactionSuccess }) {
  const [activeFormCode, setActiveFormCode] = useState('FORM_CASH_DEP');
  const [formSchema, setFormSchema] = useState(null);
  const [isLoadingSchema, setIsLoadingSchema] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [receipt, setReceipt] = useState(null);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  // Quick Account Inspector Side Panel
  const [inspectorAccount, setInspectorAccount] = useState('');
  const [inspectorData, setInspectorData] = useState(null);
  const [isInspecting, setIsInspecting] = useState(false);
  const [inspectorError, setInspectorError] = useState(null);

  // Load Form Schema when activeFormCode changes
  useEffect(() => {
    let isMounted = true;
    async function loadSchema() {
      try {
        setIsLoadingSchema(true);
        setErrorMessage(null);
        const data = await formApi.getFormByCode(activeFormCode);
        if (isMounted) {
          setFormSchema(data);
        }
      } catch (err) {
        if (isMounted) {
          setErrorMessage(`Unable to load form schema ${activeFormCode}: ${err.message}`);
        }
      } finally {
        if (isMounted) {
          setIsLoadingSchema(false);
        }
      }
    }
    loadSchema();
    return () => {
      isMounted = false;
    };
  }, [activeFormCode]);

  // Handle Form Submission
  const handleFormSubmit = async (formData) => {
    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      let responseReceipt;

      const idempotencyKey = `TELLER-${Date.now()}-${Math.random().toString(36).substring(2, 7)}`;

      if (activeFormCode === 'FORM_CASH_DEP') {
        responseReceipt = await transactionApi.deposit({
          accountNumber: formData.accountNumber,
          amount: Number(formData.amount),
          currency: formData.currency || 'VND',
          description: formData.description || 'Counter cash deposit',
          tellerId: 'TELLER01',
          branchCode: '001',
          channelId: 'TELLER',
          idempotencyKey,
        });
      } else if (activeFormCode === 'FORM_CASH_WDR') {
        responseReceipt = await transactionApi.withdraw({
          accountNumber: formData.accountNumber,
          amount: Number(formData.amount),
          currency: formData.currency || 'VND',
          description: formData.description || 'Counter cash withdrawal',
          tellerId: 'TELLER01',
          branchCode: '001',
          channelId: 'TELLER',
          idempotencyKey,
        });
      } else if (activeFormCode === 'FORM_TRF_INTERNAL') {
        responseReceipt = await transactionApi.transfer({
          sourceAccountNumber: formData.sourceAccountNumber,
          destAccountNumber: formData.destAccountNumber,
          amount: Number(formData.amount),
          feeAmount: Number(formData.feeAmount || 0),
          feePayer: formData.feePayer || 'SOURCE',
          currency: formData.currency || 'VND',
          description: formData.description || 'Internal transfer',
          tellerId: 'TELLER01',
          branchCode: '001',
          channelId: 'TELLER',
          idempotencyKey,
        });
      }

      setReceipt(responseReceipt);
      setIsReceiptOpen(true);
      if (onTransactionSuccess) onTransactionSuccess();
    } catch (err) {
      setErrorMessage(err.message || 'Transaction failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  // Inspect Account handler
  const handleInspectAccount = async (e) => {
    e?.preventDefault();
    if (!inspectorAccount.trim()) return;
    try {
      setIsInspecting(true);
      setInspectorError(null);
      setInspectorData(null);
      const data = await accountApi.getAccountDetail(inspectorAccount.trim());
      setInspectorData(data);
    } catch (err) {
      setInspectorError(err.message || 'Account details not found');
    } finally {
      setIsInspecting(false);
    }
  };

  const formatMoney = (val) => {
    if (val === undefined || val === null) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(val));
  };

  return (
    <div className="grid grid-cols-1 gap-8 lg:grid-cols-3">
      {/* Left 2 Cols: Form Selection & Dynamic Form Canvas */}
      <div className="lg:col-span-2 space-y-6">
        {/* Transaction Navigation Tabs */}
        <div className="flex flex-wrap gap-2 rounded-xl bg-slate-200/70 p-1.5">
          <button
            onClick={() => setActiveFormCode('FORM_CASH_DEP')}
            className={`flex items-center space-x-2 rounded-lg px-4 py-2.5 text-xs font-bold transition ${
              activeFormCode === 'FORM_CASH_DEP'
                ? 'bg-white text-emerald-800 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ArrowDownLeft className="h-4 w-4 text-emerald-600" />
            <span>CASH DEPOSIT (CASH_DEP)</span>
          </button>

          <button
            onClick={() => setActiveFormCode('FORM_CASH_WDR')}
            className={`flex items-center space-x-2 rounded-lg px-4 py-2.5 text-xs font-bold transition ${
              activeFormCode === 'FORM_CASH_WDR'
                ? 'bg-white text-amber-800 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ArrowUpRight className="h-4 w-4 text-amber-600" />
            <span>CASH WITHDRAWAL (CASH_WDR)</span>
          </button>

          <button
            onClick={() => setActiveFormCode('FORM_TRF_INTERNAL')}
            className={`flex items-center space-x-2 rounded-lg px-4 py-2.5 text-xs font-bold transition ${
              activeFormCode === 'FORM_TRF_INTERNAL'
                ? 'bg-white text-blue-900 shadow-sm'
                : 'text-slate-600 hover:text-slate-900'
            }`}
          >
            <ArrowLeftRight className="h-4 w-4 text-blue-700" />
            <span>INTERNAL TRANSFER (TRF_INTERNAL)</span>
          </button>
        </div>

        {/* Dynamic Form Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          {errorMessage && (
            <div className="mb-5 flex items-start space-x-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 animate-fadeIn">
              <AlertCircle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
              <div>
                <span className="font-bold">Transaction Processing Error:</span>
                <p className="mt-0.5 text-red-700">{errorMessage}</p>
              </div>
            </div>
          )}

          {isLoadingSchema ? (
            <div className="flex h-72 flex-col items-center justify-center space-y-3">
              <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
              <p className="text-xs font-medium text-slate-500">
                Loading Schema from Oracle 19c Catalog...
              </p>
            </div>
          ) : (
            <DynamicForm
              key={activeFormCode}
              schema={formSchema}
              onSubmit={handleFormSubmit}
              isSubmitting={isSubmitting}
              submitLabel="Post Transaction"
            />
          )}
        </div>
      </div>

      {/* Right 1 Col: Quick Account Inspector Widget */}
      <div className="space-y-6">
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          <div className="flex items-center space-x-2 border-b border-slate-100 pb-3">
            <Search className="h-4 w-4 text-blue-600" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Live Account Lookup
            </h3>
          </div>

          <form onSubmit={handleInspectAccount} className="mt-4 space-y-3">
            <div className="relative">
              <input
                type="text"
                placeholder="Enter 16-digit account..."
                value={inspectorAccount}
                onChange={(e) => setInspectorAccount(e.target.value)}
                className="w-full rounded-lg border border-slate-300 py-2.5 pl-3 pr-20 font-mono text-xs focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
              />
              <button
                type="submit"
                disabled={isInspecting || !inspectorAccount.trim()}
                className="absolute inset-y-1 right-1 rounded-md bg-slate-900 px-3 text-[11px] font-bold text-white hover:bg-slate-800 disabled:opacity-50 transition"
              >
                {isInspecting ? <Loader2 className="h-3 w-3 animate-spin" /> : 'Lookup'}
              </button>
            </div>
          </form>

          {inspectorError && (
            <div className="mt-3 rounded-lg bg-amber-50 p-2.5 text-xs text-amber-800 border border-amber-200">
              {inspectorError}
            </div>
          )}

          {inspectorData && (
            <div className="mt-4 space-y-3 rounded-xl bg-slate-50 p-4 border border-slate-200/80 text-xs animate-fadeIn">
              <div className="flex items-center justify-between border-b border-slate-200 pb-2">
                <span className="font-mono font-bold text-slate-800">{inspectorData.accountNumber}</span>
                <span className="rounded bg-emerald-100 px-1.5 py-0.5 text-[10px] font-bold text-emerald-800">
                  {inspectorData.status}
                </span>
              </div>
              <div className="space-y-1.5">
                <div className="flex justify-between">
                  <span className="text-slate-500">Account Holder:</span>
                  <span className="font-semibold text-slate-900">{inspectorData.accountHolderName}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">CIF Number:</span>
                  <span className="font-mono text-slate-700">{inspectorData.customerId}</span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Product:</span>
                  <span className="font-medium text-slate-700">{inspectorData.productCode}</span>
                </div>
                <div className="flex justify-between border-t border-slate-200 pt-2">
                  <span className="text-slate-500">Ledger Balance:</span>
                  <span className="font-mono font-semibold text-slate-900">
                    {formatMoney(inspectorData.currentBalance)} {inspectorData.currency}
                  </span>
                </div>
                <div className="flex justify-between">
                  <span className="text-slate-500">Hold Balance:</span>
                  <span className="font-mono font-semibold text-amber-700">
                    {formatMoney(inspectorData.holdBalance)} {inspectorData.currency}
                  </span>
                </div>
                <div className="flex justify-between border-t border-slate-200 pt-2">
                  <span className="text-emerald-700 font-semibold">Available Balance:</span>
                  <span className="font-mono font-bold text-emerald-800 text-sm">
                    {formatMoney(inspectorData.availableBalance)} {inspectorData.currency}
                  </span>
                </div>
              </div>
            </div>
          )}
        </div>

        {/* Daily Cash Drawer Summary */}
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          <div className="flex items-center space-x-2 border-b border-slate-100 pb-3">
            <Building className="h-4 w-4 text-slate-600" />
            <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
              Teller Cash Drawer
            </h3>
          </div>
          <div className="mt-4 space-y-2 text-xs">
            <div className="flex justify-between">
              <span className="text-slate-500">Cash Vault GL:</span>
              <span className="font-mono text-slate-700">10110001 (VND)</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Vault Status:</span>
              <span className="font-semibold text-emerald-700">100% Balanced</span>
            </div>
            <div className="flex justify-between">
              <span className="text-slate-500">Instant Approval Limit:</span>
              <span className="font-mono font-bold text-slate-900">500,000,000 VND</span>
            </div>
          </div>
        </div>
      </div>

      {/* Electronic Transaction Receipt Modal */}
      <ReceiptModal
        isOpen={isReceiptOpen}
        onClose={() => setIsReceiptOpen(false)}
        receipt={receipt}
      />
    </div>
  );
}

import React, { useState, useEffect } from 'react';
import DynamicForm from '../engine/DynamicForm';
import ReceiptModal from '../common/ReceiptModal';
import GenericSearchWidget from '../engine/GenericSearchWidget';
import { formApi } from '../../services/formApi';
import { transactionApi } from '../../services/transactionApi';
import { lendingApi } from '../../services/lendingApi';
import { accountApi } from '../../services/accountApi';
import apiClient from '../../services/apiClient';
import { Loader2, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function TabFormContainer({
  tab,
  onNavigate,
  onTransactionSuccess,
}) {
  const [schema, setSchema] = useState(null);
  const [isLoading, setIsLoading] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);
  const [receipt, setReceipt] = useState(null);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadFormSchema() {
      try {
        setIsLoading(true);
        setErrorMessage(null);
        setSuccessMessage(null);
        const data = await formApi.getFormByCode(tab.formCode);
        if (isMounted) setSchema(data);
      } catch (err) {
        if (isMounted) setErrorMessage(`Unable to load form schema [${tab.formCode}]: ${err.message}`);
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }
    loadFormSchema();
    return () => {
      isMounted = false;
    };
  }, [tab.formCode]);

  const handleSubmit = async (formData, action = null) => {
    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      setSuccessMessage(null);

      const idempotencyKey = `TRX-${tab.id}-${Date.now()}`;
      let resReceipt = null;

      // Submission routing based on formCode or action endpoint
      if (tab.formCode === 'FORM_CASH_DEP') {
        resReceipt = await transactionApi.deposit({
          accountNumber: formData.accountNumber,
          amount: Number(formData.amount),
          currency: formData.currency || 'VND',
          narration: formData.narration || formData.description || 'Counter cash deposit',
          description: formData.narration || formData.description || 'Counter cash deposit',
          tellerId: 'TELLER01',
          makerId: 'TELLER01',
          branchCode: 'VN001',
          channelId: formData.channelId || 'BRANCH',
          idempotencyKey,
        });
      } else if (tab.formCode === 'FORM_CASH_WDR') {
        resReceipt = await transactionApi.withdraw({
          accountNumber: formData.accountNumber,
          amount: Number(formData.amount),
          currency: formData.currency || 'VND',
          narration: formData.narration || formData.description || 'Counter cash withdrawal',
          description: formData.narration || formData.description || 'Counter cash withdrawal',
          tellerId: 'TELLER01',
          makerId: 'TELLER01',
          branchCode: 'VN001',
          channelId: formData.channelId || 'BRANCH',
          idempotencyKey,
        });
      } else if (tab.formCode === 'FORM_TRF_INTERNAL') {
        resReceipt = await transactionApi.transfer({
          fromAccountNumber: formData.fromAccountNumber || formData.sourceAccountNumber,
          toAccountNumber: formData.toAccountNumber || formData.destAccountNumber,
          sourceAccountNumber: formData.fromAccountNumber || formData.sourceAccountNumber,
          destAccountNumber: formData.toAccountNumber || formData.destAccountNumber,
          amount: Number(formData.amount),
          feeAmount: Number(formData.feeAmount || 0),
          feePayer: formData.feePayer || 'SOURCE',
          currency: formData.currency || 'VND',
          narration: formData.narration || formData.description || 'Internal transfer',
          description: formData.narration || formData.description || 'Internal transfer',
          tellerId: 'TELLER01',
          makerId: 'TELLER01',
          branchCode: 'VN001',
          channelId: formData.channelId || 'BRANCH',
          idempotencyKey,
        });
      } else if (tab.formCode === 'FORM_CREDIT_ACCOUNT_OPEN') {
        const payload = {
          customerId: Number(formData.customerId || 1001),
          disbursementAccountNumber: formData.linkedCasaAccountNumber,
          principalAmount: Number(formData.principalAmount),
          termMonths: Number(formData.termMonths),
          interestRate: Number(formData.interestRate || 0.12),
          ifcCode: formData.ifcCode || 'IFC_STD_MTH',
          marginCode: formData.marginCode || 'MRG_STD',
          repaymentType: formData.repaymentType || 'EQUAL_INSTALLMENT',
          startDate: formData.startDate || new Date().toISOString().split('T')[0],
        };
        const loanRes = await lendingApi.createContract(payload);
        resReceipt = {
          trxId: `LOAN-${loanRes.loanId}`,
          trxType: 'LOAN_ORIGINATION',
          amount: formData.principalAmount,
          currency: 'VND',
          accountNumber: formData.linkedCasaAccountNumber,
          description: `Credit Contract [${loanRes.loanId}] created successfully`,
        };
        setSuccessMessage(`Credit contract [${loanRes.loanId}] originated successfully!`);
      } else if (tab.formCode === 'FORM_ACCOUNT_OPEN') {
        const accRes = await accountApi.openAccount({
          customerId: Number(formData.customerId || 1001),
          productCode: formData.productCode || 'CASA_VND',
          branchCode: formData.branchCode || '001',
          currency: formData.currency || 'VND',
        });
        resReceipt = {
          trxId: `ACC-${accRes.accountNumber}`,
          trxType: 'ACCOUNT_OPEN',
          amount: 0,
          currency: formData.currency || 'VND',
          accountNumber: accRes.accountNumber,
          description: `Deposit CASA account opened successfully: ${accRes.accountNumber}`,
        };
        setSuccessMessage(`Deposit account [${accRes.accountNumber}] opened successfully!`);
      } else if (tab.formCode === 'FORM_CUST_CREATE') {
        const custRes = await apiClient.post('/customers', {
          fullName: formData.fullName,
          nationalId: formData.nationalId,
          customerType: formData.customerType || 'INDIVIDUAL',
          kycStatus: formData.kycStatus || 'VERIFIED',
          status: 'ACTIVE',
          gender: formData.gender || 'OTHER',
          phoneNumber: formData.phoneNumber || null,
          emailAddress: formData.emailAddress || null,
          taxNumber: formData.taxNumber || null,
          streetAddress: formData.streetAddress || null,
          commune: formData.commune || null,
          district: formData.district || null,
          province: formData.province || null,
          country: formData.country || 'VN',
        });
        resReceipt = {
          trxId: custRes.data.cifNumber,
          trxType: 'CUSTOMER_REGISTRATION',
          amount: 0,
          currency: 'VND',
          description: `Customer CIF [${custRes.data.cifNumber}] registered for ${formData.fullName}`,
        };
        setSuccessMessage(`Customer profile [${custRes.data.cifNumber}] registered successfully!`);
      } else {
        // Generic endpoint submission
        const targetEndpoint = action?.endpoint || schema.submitEndpoint;
        if (targetEndpoint) {
          const customRes = await apiClient.post(targetEndpoint, formData);
          setSuccessMessage(`Operation completed successfully.`);
        }
      }

      if (resReceipt) {
        setReceipt(resReceipt);
        setIsReceiptOpen(true);
      }

      if (onTransactionSuccess) onTransactionSuccess();
    } catch (err) {
      setErrorMessage(err.message || 'Transaction submission failed');
    } finally {
      setIsSubmitting(false);
    }
  };

  if (isLoading) {
    return (
      <div className="flex h-72 flex-col items-center justify-center space-y-3 rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
        <p className="text-xs font-medium text-slate-500">
          Loading Form Schema [${tab.formCode}] from Oracle 19c Catalog...
        </p>
      </div>
    );
  }

  if (schema?.formType === 'SEARCH_GRID') {
    return (
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs animate-fadeIn">
        <GenericSearchWidget schema={schema} onNavigate={onNavigate} />
      </div>
    );
  }

  return (
    <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-5 animate-fadeIn">
      {/* Alert Notices */}
      {errorMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <AlertCircle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Transaction Error:</span>
            <p className="mt-0.5 text-red-700">{errorMessage}</p>
          </div>
        </div>
      )}

      {successMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Success:</span>
            <p className="mt-0.5 text-emerald-700">{successMessage}</p>
          </div>
        </div>
      )}

      {/* Render Dynamic Form */}
      <DynamicForm
        key={`${tab.id}-${tab.formCode}`}
        schema={schema}
        initialData={tab.initialParams || {}}
        onSubmit={handleSubmit}
        onNavigate={onNavigate}
        isSubmitting={isSubmitting}
        submitLabel="Submit"
      />

      {/* Electronic Transaction Receipt Modal */}
      <ReceiptModal
        isOpen={isReceiptOpen}
        onClose={() => setIsReceiptOpen(false)}
        receipt={receipt}
      />
    </div>
  );
}

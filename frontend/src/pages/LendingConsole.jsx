import React, { useState, useEffect } from 'react';
import DynamicForm from '../components/engine/DynamicForm';
import ReceiptModal from '../components/common/ReceiptModal';
import { formApi } from '../services/formApi';
import { lendingApi } from '../services/lendingApi';
import {
  FileText,
  DollarSign,
  Calendar,
  CheckCircle2,
  AlertCircle,
  Loader2,
  Percent,
  Layers,
  ChevronRight,
} from 'lucide-react';

export default function LendingConsole({ onTransactionSuccess }) {
  const [activeTab, setActiveTab] = useState('origination'); // 'origination' | 'disbursement'
  const [originationSchema, setOriginationSchema] = useState(null);
  const [isLoadingSchema, setIsLoadingSchema] = useState(true);
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successNotice, setSuccessNotice] = useState(null);

  // Disbursement state
  const [disburseLoanId, setDisburseLoanId] = useState('');
  const [disburseAccount, setDisburseAccount] = useState('');
  const [disburseAmount, setDisburseAmount] = useState('');
  const [isDisbursing, setIsDisbursing] = useState(false);
  const [disburseReceipt, setDisburseReceipt] = useState(null);
  const [isReceiptOpen, setIsReceiptOpen] = useState(false);

  // Schedule Viewer state
  const [inspectLoanId, setInspectLoanId] = useState('');
  const [schedules, setSchedules] = useState(null);
  const [isLoadingSchedules, setIsLoadingSchedules] = useState(false);

  useEffect(() => {
    let isMounted = true;
    async function loadSchema() {
      try {
        setIsLoadingSchema(true);
        const data = await formApi.getFormByCode('FORM_LOAN_ORIGINATION');
        if (isMounted) setOriginationSchema(data);
      } catch (err) {
        if (isMounted) setErrorMessage(`Lỗi nạp form tín dụng: ${err.message}`);
      } finally {
        if (isMounted) setIsLoadingSchema(false);
      }
    }
    loadSchema();
    return () => {
      isMounted = false;
    };
  }, []);

  const handleCreateContract = async (formData) => {
    try {
      setIsSubmitting(true);
      setErrorMessage(null);
      setSuccessNotice(null);

      const payload = {
        customerId: formData.customerId,
        disbursementAccountNumber: formData.disbursementAccountNumber,
        principalAmount: Number(formData.principalAmount),
        termMonths: Number(formData.termMonths),
        interestRate: Number(formData.interestRate),
        ifcCode: formData.ifcCode || 'IFC_STD_MTH',
        marginCode: formData.marginCode || 'MRG_STD',
        repaymentType: formData.repaymentType || 'EQUAL_INSTALLMENT',
        startDate: formData.startDate || new Date().toISOString().split('T')[0],
      };

      const result = await lendingApi.createContract(payload);
      setSuccessNotice(
        `Đã khởi tạo thành công Hợp đồng Vay số [${result.loanId}]. Bạn có thể chuyển sang tab Giải Ngân để cấp vốn!`
      );
      setDisburseLoanId(result.loanId);
      setDisburseAccount(formData.disbursementAccountNumber);
      setDisburseAmount(formData.principalAmount);
      if (onTransactionSuccess) onTransactionSuccess();
    } catch (err) {
      setErrorMessage(err.message || 'Lỗi lập hợp đồng tín dụng');
    } finally {
      setIsSubmitting(false);
    }
  };

  const handleDisburse = async (e) => {
    e.preventDefault();
    if (!disburseLoanId.trim() || !disburseAccount.trim()) return;

    try {
      setIsDisbursing(true);
      setErrorMessage(null);
      const res = await lendingApi.disburse({
        loanId: disburseLoanId.trim(),
        targetAccountNumber: disburseAccount.trim(),
        amount: Number(disburseAmount),
        channelId: 'CREDIT_OFFICER',
        idempotencyKey: `DISB-${disburseLoanId}-${Date.now()}`,
      });

      setDisburseReceipt({
        trxId: res.transactionId || `DISB-${disburseLoanId}`,
        journalId: res.journalId,
        trxType: 'LOAN_DISBURSEMENT',
        amount: res.disbursedAmount || disburseAmount,
        currency: 'VND',
        destAccountNumber: disburseAccount,
        description: `Giải ngân Hợp đồng vay ${disburseLoanId}`,
      });
      setIsReceiptOpen(true);
      setInspectLoanId(disburseLoanId);
      loadRepaymentSchedules(disburseLoanId);
      if (onTransactionSuccess) onTransactionSuccess();
    } catch (err) {
      setErrorMessage(err.message || 'Lỗi giải ngân vốn');
    } finally {
      setIsDisbursing(false);
    }
  };

  const loadRepaymentSchedules = async (loanId) => {
    if (!loanId) return;
    try {
      setIsLoadingSchedules(true);
      const data = await lendingApi.getSchedules(loanId);
      setSchedules(data);
    } catch (err) {
      console.warn('Cannot load schedules:', err.message);
    } finally {
      setIsLoadingSchedules(false);
    }
  };

  const formatMoney = (val) => {
    if (val === undefined || val === null) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(val));
  };

  return (
    <div className="space-y-6">
      {/* Sub Tabs Header */}
      <div className="flex border-b border-slate-200">
        <button
          onClick={() => setActiveTab('origination')}
          className={`flex items-center space-x-2 border-b-2 px-6 py-3 text-sm font-bold transition ${
            activeTab === 'origination'
              ? 'border-blue-700 text-blue-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <FileText className="h-4 w-4" />
          <span>LẬP HỢP ĐỒNG TÍN DỤNG (ORIGINATION)</span>
        </button>

        <button
          onClick={() => setActiveTab('disbursement')}
          className={`flex items-center space-x-2 border-b-2 px-6 py-3 text-sm font-bold transition ${
            activeTab === 'disbursement'
              ? 'border-blue-700 text-blue-700'
              : 'border-transparent text-slate-500 hover:text-slate-800'
          }`}
        >
          <DollarSign className="h-4 w-4" />
          <span>GIẢI NGÂN & LỊCH TRẢ NỢ (DISBURSE & SCHEDULE)</span>
        </button>
      </div>

      {/* Global Alerts */}
      {errorMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <AlertCircle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Lỗi nghiệp vụ Tín Dụng:</span>
            <p className="mt-0.5 text-red-700">{errorMessage}</p>
          </div>
        </div>
      )}

      {successNotice && (
        <div className="flex items-start space-x-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 animate-fadeIn">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Khởi tạo hợp đồng thành công:</span>
            <p className="mt-0.5 text-emerald-700">{successNotice}</p>
          </div>
        </div>
      )}

      {/* TAB 1: ORIGINATION */}
      {activeTab === 'origination' && (
        <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
          {isLoadingSchema ? (
            <div className="flex h-72 flex-col items-center justify-center space-y-3">
              <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
              <p className="text-xs font-medium text-slate-500">Đang nạp Schema Khế Ước Vay...</p>
            </div>
          ) : (
            <DynamicForm
              schema={originationSchema}
              onSubmit={handleCreateContract}
              isSubmitting={isSubmitting}
              submitLabel="Phê Duyệt & Mở Hợp Đồng"
            />
          )}
        </div>
      )}

      {/* TAB 2: DISBURSEMENT & SCHEDULE */}
      {activeTab === 'disbursement' && (
        <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
          {/* Left Column: Disburse Action Form */}
          <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-5">
            <div className="border-b border-slate-100 pb-3">
              <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800">
                Thực Hiện Giải Ngân (Disburse)
              </h3>
              <p className="text-xs text-slate-500">
                Bơm vốn trực tiếp vào tài khoản CASA của khách hàng
              </p>
            </div>

            <form onSubmit={handleDisburse} className="space-y-4">
              <div>
                <label className="text-xs font-semibold uppercase text-slate-600">
                  Mã Hợp Đồng Vay (Loan ID) *
                </label>
                <input
                  type="text"
                  required
                  placeholder="e.g. LN-2026-..."
                  value={disburseLoanId}
                  onChange={(e) => setDisburseLoanId(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 font-mono text-sm focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div>
                <label className="text-xs font-semibold uppercase text-slate-600">
                  Tài Khoản Thụ Hưởng (CASA) *
                </label>
                <input
                  type="text"
                  required
                  placeholder="Số tài khoản 16 số..."
                  value={disburseAccount}
                  onChange={(e) => setDisburseAccount(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 font-mono text-sm focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <div>
                <label className="text-xs font-semibold uppercase text-slate-600">
                  Số Tiền Giải Ngân (VND) *
                </label>
                <input
                  type="number"
                  required
                  placeholder="0"
                  value={disburseAmount}
                  onChange={(e) => setDisburseAmount(e.target.value)}
                  className="mt-1 w-full rounded-lg border border-slate-300 px-3 py-2.5 font-mono text-sm font-bold text-slate-900 focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
                />
              </div>

              <button
                type="submit"
                disabled={isDisbursing}
                className="w-full inline-flex justify-center items-center space-x-2 rounded-lg bg-emerald-700 py-3 text-sm font-bold text-white shadow-sm hover:bg-emerald-800 disabled:opacity-50 transition"
              >
                {isDisbursing ? (
                  <>
                    <Loader2 className="h-4 w-4 animate-spin" />
                    <span>Đang Giải Ngân...</span>
                  </>
                ) : (
                  <>
                    <DollarSign className="h-4 w-4" />
                    <span>Xác Nhận Giải Ngân Vốn</span>
                  </>
                )}
              </button>
            </form>
          </div>

          {/* Right 2 Columns: Repayment Schedule Viewer */}
          <div className="lg:col-span-2 rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
            <div className="flex flex-wrap items-center justify-between border-b border-slate-100 pb-3 gap-3">
              <div>
                <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800">
                  Lịch Thu Nợ Định Kỳ (Amortization Schedule)
                </h3>
                <p className="text-xs text-slate-500">
                  Chi tiết gốc lãi phân bổ từng kỳ theo mô hình Waterfall
                </p>
              </div>

              <div className="flex items-center space-x-2">
                <input
                  type="text"
                  placeholder="Mã Loan ID..."
                  value={inspectLoanId}
                  onChange={(e) => setInspectLoanId(e.target.value)}
                  className="rounded-lg border border-slate-300 px-3 py-1.5 font-mono text-xs focus:border-blue-600 focus:outline-none"
                />
                <button
                  type="button"
                  onClick={() => loadRepaymentSchedules(inspectLoanId)}
                  disabled={isLoadingSchedules || !inspectLoanId.trim()}
                  className="rounded-lg bg-slate-900 px-3 py-1.5 text-xs font-bold text-white hover:bg-slate-800 disabled:opacity-50 transition"
                >
                  {isLoadingSchedules ? <Loader2 className="h-3 w-3 animate-spin" /> : 'Tra Cứu'}
                </button>
              </div>
            </div>

            {schedules && schedules.length > 0 ? (
              <div className="overflow-x-auto">
                <table className="w-full text-left text-xs">
                  <thead className="bg-slate-50 uppercase text-[10px] font-bold text-slate-500 border-b border-slate-200">
                    <tr>
                      <th className="px-3 py-2.5">Kỳ</th>
                      <th className="px-3 py-2.5">Ngày Đến Hạn</th>
                      <th className="px-3 py-2.5 text-right">Gốc Phải Trả</th>
                      <th className="px-3 py-2.5 text-right">Lãi Dự Tính</th>
                      <th className="px-3 py-2.5 text-right">Tổng Kỳ (EMI)</th>
                      <th className="px-3 py-2.5 text-right">Dư Nợ Còn Lại</th>
                      <th className="px-3 py-2.5 text-center">Trạng Thái</th>
                    </tr>
                  </thead>
                  <tbody className="divide-y divide-slate-100 font-mono">
                    {schedules.map((row) => (
                      <tr key={row.periodNumber} className="hover:bg-slate-50/70 transition">
                        <td className="px-3 py-2 font-bold text-slate-800">#{row.periodNumber}</td>
                        <td className="px-3 py-2 text-slate-600">{row.dueDate}</td>
                        <td className="px-3 py-2 text-right font-semibold text-slate-800">
                          {formatMoney(row.principalDue)}
                        </td>
                        <td className="px-3 py-2 text-right text-slate-600">
                          {formatMoney(row.interestDue)}
                        </td>
                        <td className="px-3 py-2 text-right font-bold text-blue-900">
                          {formatMoney(row.totalDue)}
                        </td>
                        <td className="px-3 py-2 text-right text-slate-500">
                          {formatMoney(row.outstandingPrincipal)}
                        </td>
                        <td className="px-3 py-2 text-center">
                          <span
                            className={`rounded px-1.5 py-0.5 text-[10px] font-bold ${
                              row.status === 'PAID'
                                ? 'bg-emerald-100 text-emerald-800'
                                : row.status === 'OVERDUE'
                                ? 'bg-red-100 text-red-800'
                                : 'bg-slate-100 text-slate-700'
                            }`}
                          >
                            {row.status || 'SCHEDULED'}
                          </span>
                        </td>
                      </tr>
                    ))}
                  </tbody>
                </table>
              </div>
            ) : (
              <div className="flex h-48 flex-col items-center justify-center space-y-2 rounded-xl border border-dashed border-slate-200">
                <Calendar className="h-8 w-8 text-slate-300" />
                <p className="text-xs text-slate-400">
                  Nhập mã Loan ID hoặc giải ngân hợp đồng để xem bảng khấu hao kỳ hạn
                </p>
              </div>
            )}
          </div>
        </div>
      )}

      <ReceiptModal
        isOpen={isReceiptOpen}
        onClose={() => setIsReceiptOpen(false)}
        receipt={disburseReceipt}
      />
    </div>
  );
}

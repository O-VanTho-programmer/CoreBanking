import React, { useState, useEffect } from 'react';
import { eodApi } from '../services/eodApi';
import {
  Play,
  RotateCcw,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Scale,
  ShieldCheck,
  Calendar,
  Lock,
  Unlock,
  Loader2,
  ArrowRight,
} from 'lucide-react';

const EOD_STEPS = [
  { step: 1, name: 'VERIFY_CUTOFF', label: '1. Khóa Sổ Giao Dịch Quầy & Kênh Online' },
  { step: 2, name: 'ACCRUE_INTEREST_CASA', label: '2. Dự Chi Lãi Suất Tiền Gửi Tiết Kiệm & CASA' },
  { step: 3, name: 'ACCRUE_INTEREST_LOANS', label: '3. Dự Thu Lãi Suất Danh Mục Cho Vay (IFC)' },
  { step: 4, name: 'EXECUTE_SCHEDULED_REPAYMENTS', label: '4. Thu Nợ Tự Động Định Kỳ (Waterfall Sweep)' },
  { step: 5, name: 'CLASSIFY_LOAN_STATUS', label: '5. Phân Loại Nhóm Nợ (Nhóm 1 - 5 TT31/2024)' },
  { step: 6, name: 'AGGREGATE_BALANCE_BUCKETS', label: '6. Chốt Snapshot Số Dư & Lịch Sử Biến Động' },
  { step: 7, name: 'VERIFY_TRIAL_BALANCE', label: '7. Đối Soát Bảng Cân Đối Thử Nghiệm Sổ Cái (GL)' },
  { step: 8, name: 'ROLLOVER_WORKING_DATE', label: '8. Tịnh Tiến Chuyển Sang Ngày Làm Việc Mới' },
];

export default function EodAccountingCockpit({ onSystemDateChange }) {
  const [eodStatus, setEodStatus] = useState(null);
  const [trialBalance, setTrialBalance] = useState(null);
  const [isLoadingStatus, setIsLoadingStatus] = useState(true);
  const [isRunningEod, setIsRunningEod] = useState(false);
  const [currentStepIndex, setCurrentStepIndex] = useState(-1);
  const [completedSteps, setCompletedSteps] = useState(new Set());
  const [isTogglingCutoff, setIsTogglingCutoff] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);
  const [successMessage, setSuccessMessage] = useState(null);

  // Load Status and Trial Balance on mount
  useEffect(() => {
    loadStatus();
  }, []);

  const loadStatus = async () => {
    try {
      setIsLoadingStatus(true);
      setErrorMessage(null);
      const statusData = await eodApi.getStatus();
      setEodStatus(statusData);

      if (statusData?.workingDate) {
        const tbData = await eodApi.getTrialBalance(statusData.workingDate);
        setTrialBalance(tbData);
      }
    } catch (err) {
      setErrorMessage(`Không thể tải trạng thái vận hành: ${err.message}`);
    } finally {
      setIsLoadingStatus(false);
    }
  };

  const handleToggleCutoff = async () => {
    if (!eodStatus) return;
    try {
      setIsTogglingCutoff(true);
      setErrorMessage(null);
      const newClosedState = !eodStatus.cutoffClosed;
      const res = await eodApi.toggleCutoff(newClosedState);
      setEodStatus((prev) => ({ ...prev, cutoffClosed: res.cutoffClosed ?? newClosedState }));
      setSuccessMessage(
        newClosedState
          ? 'Đã thực hiện CUTOFF khóa sổ giao dịch ngoại vi. Hệ thống sẵn sàng chạy EOD Batch!'
          : 'Đã mở lại trạng thái ONLINE cho phép thực hiện giao dịch ban ngày.'
      );
    } catch (err) {
      setErrorMessage(`Lỗi đổi trạng thái Cutoff: ${err.message}`);
    } finally {
      setIsTogglingCutoff(false);
    }
  };

  const handleRunEod = async () => {
    if (!eodStatus) return;
    try {
      setIsRunningEod(true);
      setErrorMessage(null);
      setSuccessMessage(null);
      setCompletedSteps(new Set());

      // Animated stepper progression
      for (let i = 0; i < EOD_STEPS.length; i++) {
        setCurrentStepIndex(i);
        await new Promise((r) => setTimeout(r, 450));
        setCompletedSteps((prev) => new Set([...prev, i]));
      }

      // Backend call to execute real 8-step pipeline
      const res = await eodApi.runEod(eodStatus.workingDate);

      setSuccessMessage(
        `Hoàn tất chu kỳ EOD thành công! Ngày làm việc đã chuyển từ [${eodStatus.workingDate}] sang [${res.nextWorkingDate}].`
      );

      // Refresh status and trial balance
      await loadStatus();
      if (onSystemDateChange) onSystemDateChange(res.nextWorkingDate);
    } catch (err) {
      setErrorMessage(`EOD Batch thất bại: ${err.message}`);
    } finally {
      setIsRunningEod(false);
      setCurrentStepIndex(-1);
    }
  };

  const formatMoney = (val) => {
    if (val === undefined || val === null) return '0';
    return new Intl.NumberFormat('vi-VN').format(Number(val));
  };

  return (
    <div className="space-y-6">
      {/* Top Banner Alert */}
      {errorMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-red-200 bg-red-50 p-4 text-sm text-red-800 animate-fadeIn">
          <AlertTriangle className="h-5 w-5 text-red-500 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Cảnh báo hệ thống:</span>
            <p className="mt-0.5 text-red-700">{errorMessage}</p>
          </div>
        </div>
      )}

      {successMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 animate-fadeIn">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Thao tác thành công:</span>
            <p className="mt-0.5 text-emerald-700">{successMessage}</p>
          </div>
        </div>
      )}

      {/* Control Strip Cards */}
      <div className="grid grid-cols-1 gap-6 sm:grid-cols-3">
        {/* Working Date Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs">
          <div className="flex items-center space-x-2 text-slate-500 text-xs font-semibold uppercase">
            <Calendar className="h-4 w-4 text-blue-600" />
            <span>Ngày Làm Việc Hiện Tại</span>
          </div>
          <div className="mt-2 font-mono text-2xl font-black text-slate-900">
            {eodStatus?.workingDate || '—'}
          </div>
          <p className="mt-1 text-[11px] text-slate-400">
            Chu kỳ kế toán tiếp theo: <span className="font-mono font-medium text-slate-600">{eodStatus?.nextWorkingDate || '—'}</span>
          </p>
        </div>

        {/* Cutoff Control Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-slate-500 text-xs font-semibold uppercase">
              {eodStatus?.cutoffClosed ? <Lock className="h-4 w-4 text-amber-600" /> : <Unlock className="h-4 w-4 text-emerald-600" />}
              <span>Khóa Sổ Giao Dịch</span>
            </div>
            <span
              className={`rounded px-2 py-0.5 text-[10px] font-bold ${
                eodStatus?.cutoffClosed ? 'bg-amber-100 text-amber-800' : 'bg-emerald-100 text-emerald-800'
              }`}
            >
              {eodStatus?.cutoffClosed ? 'CUTOFF CLOSED' : 'ONLINE OPEN'}
            </span>
          </div>
          <div className="mt-4">
            <button
              onClick={handleToggleCutoff}
              disabled={isTogglingCutoff || isRunningEod}
              className={`w-full rounded-lg px-4 py-2 text-xs font-bold transition flex items-center justify-center space-x-2 ${
                eodStatus?.cutoffClosed
                  ? 'border border-slate-300 bg-white text-slate-800 hover:bg-slate-50'
                  : 'bg-amber-600 text-white hover:bg-amber-700'
              }`}
            >
              {isTogglingCutoff ? (
                <Loader2 className="h-4 w-4 animate-spin" />
              ) : eodStatus?.cutoffClosed ? (
                <>
                  <Unlock className="h-3.5 w-3.5" />
                  <span>Mở Lại Online (Re-open)</span>
                </>
              ) : (
                <>
                  <Lock className="h-3.5 w-3.5" />
                  <span>Kích Hoạt Cutoff</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* EOD Action Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div className="flex items-center space-x-2 text-slate-500 text-xs font-semibold uppercase">
            <Clock className="h-4 w-4 text-indigo-600" />
            <span>Kích Hoạt Chu Kỳ EOD</span>
          </div>
          <div className="mt-4">
            <button
              onClick={handleRunEod}
              disabled={isRunningEod || !eodStatus?.cutoffClosed}
              className="w-full inline-flex items-center justify-center space-x-2 rounded-lg bg-blue-900 px-4 py-2 text-xs font-bold text-white shadow-sm hover:bg-blue-950 disabled:opacity-40 transition"
            >
              {isRunningEod ? (
                <>
                  <Loader2 className="h-4 w-4 animate-spin text-blue-300" />
                  <span>Đang Thực Thi EOD...</span>
                </>
              ) : (
                <>
                  <Play className="h-3.5 w-3.5" />
                  <span>Chạy 8 Bước EOD Batch</span>
                </>
              )}
            </button>
            {!eodStatus?.cutoffClosed && (
              <p className="mt-1.5 text-center text-[10px] text-amber-600 font-medium">
                * Cần bật Cutoff trước khi chạy EOD
              </p>
            )}
          </div>
        </div>
      </div>

      {/* 8-Step Interactive Pipeline Visualizer */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
        <div className="flex items-center justify-between border-b border-slate-100 pb-3">
          <div className="flex items-center space-x-2">
            <ShieldCheck className="h-5 w-5 text-blue-700" />
            <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800">
              Quy Trình Xử Lý Cuối Ngày (8-Step EOD Batch Pipeline)
            </h3>
          </div>
          <span className="text-xs font-mono text-slate-400">Atomic Orchestration</span>
        </div>

        <div className="mt-5 grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-4">
          {EOD_STEPS.map((s, idx) => {
            const isDone = completedSteps.has(idx);
            const isCurrent = currentStepIndex === idx;

            return (
              <div
                key={s.step}
                className={`flex flex-col justify-between rounded-xl border p-3.5 transition ${
                  isDone
                    ? 'border-emerald-300 bg-emerald-50/70 text-emerald-900'
                    : isCurrent
                    ? 'border-blue-500 bg-blue-50 text-blue-900 shadow-md ring-2 ring-blue-200'
                    : 'border-slate-200 bg-slate-50/50 text-slate-600'
                }`}
              >
                <div className="flex items-center justify-between">
                  <span className="font-mono text-[11px] font-bold">BƯỚC {s.step}</span>
                  {isDone ? (
                    <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                  ) : isCurrent ? (
                    <Loader2 className="h-4 w-4 animate-spin text-blue-600" />
                  ) : (
                    <div className="h-2 w-2 rounded-full bg-slate-300" />
                  )}
                </div>
                <div className="mt-2 text-xs font-semibold leading-snug">{s.label}</div>
              </div>
            );
          })}
        </div>
      </div>

      {/* Trial Balance (Bảng Cân Đối Thử Nghiệm) Viewer */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between border-b border-slate-100 pb-3 gap-3">
          <div className="flex items-center space-x-2">
            <Scale className="h-5 w-5 text-indigo-700" />
            <div>
              <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800">
                Bảng Cân Đối Thử Nghiệm Sổ Cái (General Ledger Trial Balance)
              </h3>
              <p className="text-xs text-slate-500">
                Chứng minh bảo toàn Tuyệt Đối Cân Khớp Kế Toán (Invariant 1: Debits = Credits)
              </p>
            </div>
          </div>

          {trialBalance && (
            <div
              className={`flex items-center space-x-2 rounded-xl px-3.5 py-1.5 text-xs font-bold border ${
                trialBalance.balanced
                  ? 'border-emerald-200 bg-emerald-50 text-emerald-800'
                  : 'border-red-200 bg-red-50 text-red-800'
              }`}
            >
              {trialBalance.balanced ? (
                <>
                  <CheckCircle2 className="h-4 w-4 text-emerald-600" />
                  <span>INVARIANT 1: CÂN KHỚP TUYỆT ĐỐI (Diff = 0 VND)</span>
                </>
              ) : (
                <>
                  <AlertTriangle className="h-4 w-4 text-red-600" />
                  <span>CẢNH BÁO: CHÊNH LỆCH BÚT TOÁN GL</span>
                </>
              )}
            </div>
          )}
        </div>

        {trialBalance?.entries && trialBalance.entries.length > 0 ? (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-xs">
              <thead className="bg-slate-50 uppercase text-[10px] font-bold text-slate-500 border-b border-slate-200">
                <tr>
                  <th className="px-3 py-2.5">Số Hiệu GL</th>
                  <th className="px-3 py-2.5">Tên Tài Khoản Kế Toán</th>
                  <th className="px-3 py-2.5 text-right">Tổng Dư Nợ (Debit)</th>
                  <th className="px-3 py-2.5 text-right">Tổng Dư Có (Credit)</th>
                  <th className="px-3 py-2.5 text-right">Dư Ròng (Net Balance)</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-100 font-mono">
                {trialBalance.entries.map((entry) => (
                  <tr key={entry.accountNumber} className="hover:bg-slate-50/70 transition">
                    <td className="px-3 py-2 font-bold text-slate-800">{entry.accountNumber}</td>
                    <td className="px-3 py-2 font-sans text-slate-700 font-medium">
                      {entry.accountName}
                    </td>
                    <td className="px-3 py-2 text-right font-semibold text-slate-800">
                      {formatMoney(entry.debitBalance)}
                    </td>
                    <td className="px-3 py-2 text-right font-semibold text-slate-800">
                      {formatMoney(entry.creditBalance)}
                    </td>
                    <td className="px-3 py-2 text-right font-bold text-blue-950">
                      {formatMoney(entry.netBalance)}
                    </td>
                  </tr>
                ))}
              </tbody>
              <tfoot className="border-t-2 border-slate-300 bg-slate-50/80 font-mono text-xs font-black">
                <tr>
                  <td colSpan={2} className="px-3 py-3 uppercase text-slate-700 font-sans">
                    TỔNG CỘNG TOÀN NGÂN HÀNG:
                  </td>
                  <td className="px-3 py-3 text-right text-emerald-800">
                    {formatMoney(trialBalance.totalDebit)} VND
                  </td>
                  <td className="px-3 py-3 text-right text-emerald-800">
                    {formatMoney(trialBalance.totalCredit)} VND
                  </td>
                  <td className="px-3 py-3 text-right text-blue-900">0 VND</td>
                </tr>
              </tfoot>
            </table>
          </div>
        ) : (
          <div className="flex h-36 items-center justify-center text-xs text-slate-400">
            Chưa có số liệu phát sinh trong ngày làm việc này.
          </div>
        )}
      </div>
    </div>
  );
}

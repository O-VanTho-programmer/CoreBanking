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
  { step: 1, name: 'VERIFY_CUTOFF', label: '1. Verify Cutoff & Freeze External Channels' },
  { step: 2, name: 'ACCRUE_INTEREST_CASA', label: '2. Accrue Daily CASA Deposit Interest (Expense)' },
  { step: 3, name: 'ACCRUE_INTEREST_LOANS', label: '3. Accrue Daily Loan Interest Receivable (IFC)' },
  { step: 4, name: 'EXECUTE_SCHEDULED_REPAYMENTS', label: '4. Execute Repayments (Waterfall Auto-Sweep)' },
  { step: 5, name: 'CLASSIFY_LOAN_STATUS', label: '5. Classify Loan Asset Quality (Circular 31)' },
  { step: 6, name: 'AGGREGATE_BALANCE_BUCKETS', label: '6. Snapshot GL Balance Buckets & History' },
  { step: 7, name: 'VERIFY_TRIAL_BALANCE', label: '7. Reconcile GL Trial Balance (Invariant 1)' },
  { step: 8, name: 'ROLLOVER_WORKING_DATE', label: '8. Rollover System To Next Working Date' },
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
      const normalizedStatus = {
        ...statusData,
        workingDate: statusData?.workingDate || statusData?.currentWorkingDate,
        nextWorkingDate: statusData?.nextWorkingDate,
        cutoffClosed: statusData?.cutoffClosed ?? statusData?.isCutoff ?? false,
      };
      setEodStatus(normalizedStatus);

      if (normalizedStatus?.workingDate) {
        const tbData = await eodApi.getTrialBalance(normalizedStatus.workingDate);
        setTrialBalance(tbData);
      }
    } catch (err) {
      setErrorMessage(`Unable to load operational status: ${err.message}`);
    } finally {
      setIsLoadingStatus(false);
    }
  };

  const handleToggleCutoff = async () => {
    if (!eodStatus) return;
    try {
      setIsTogglingCutoff(true);
      setErrorMessage(null);
      const newClosedState = !Boolean(eodStatus.cutoffClosed);
      const res = await eodApi.toggleCutoff(newClosedState);
      const resolvedClosed = res?.cutoffClosed ?? res?.isCutoff ?? newClosedState;
      setEodStatus((prev) => ({
        ...prev,
        cutoffClosed: resolvedClosed,
        workingDate: res?.workingDate || res?.currentWorkingDate || prev?.workingDate,
        nextWorkingDate: res?.nextWorkingDate || prev?.nextWorkingDate,
      }));
      setSuccessMessage(
        resolvedClosed
          ? 'Cutoff executed. External channels frozen. Ready for EOD Batch pipeline.'
          : 'Online mode re-opened. External transactions enabled.'
      );
    } catch (err) {
      setErrorMessage(`Error toggling Cutoff: ${err.response?.data?.message || err.message}`);
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
        `EOD Batch cycle completed successfully! Working date transitioned from [${eodStatus.workingDate}] to [${res.nextWorkingDate}].`
      );

      // Refresh status and trial balance
      await loadStatus();
      if (onSystemDateChange) onSystemDateChange(res.nextWorkingDate);
    } catch (err) {
      setErrorMessage(`EOD Batch failed: ${err.message}`);
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
            <span className="font-bold">System Warning:</span>
            <p className="mt-0.5 text-red-700">{errorMessage}</p>
          </div>
        </div>
      )}

      {successMessage && (
        <div className="flex items-start space-x-3 rounded-lg border border-emerald-200 bg-emerald-50 p-4 text-sm text-emerald-800 animate-fadeIn">
          <CheckCircle2 className="h-5 w-5 text-emerald-600 shrink-0 mt-0.5" />
          <div>
            <span className="font-bold">Operation Succeeded:</span>
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
            <span>Current Working Date</span>
          </div>
          <div className="mt-2 font-mono text-2xl font-black text-slate-900">
            {eodStatus?.workingDate || '—'}
          </div>
          <p className="mt-1 text-[11px] text-slate-400">
            Next Accounting Cycle: <span className="font-mono font-medium text-slate-600">{eodStatus?.nextWorkingDate || '—'}</span>
          </p>
        </div>

        {/* Cutoff Control Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div className="flex items-center justify-between">
            <div className="flex items-center space-x-2 text-slate-500 text-xs font-semibold uppercase">
              {eodStatus?.cutoffClosed ? <Lock className="h-4 w-4 text-amber-600" /> : <Unlock className="h-4 w-4 text-emerald-600" />}
              <span>Channel Cutoff Control</span>
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
                  <span>Re-open Online</span>
                </>
              ) : (
                <>
                  <Lock className="h-3.5 w-3.5" />
                  <span>Activate Cutoff</span>
                </>
              )}
            </button>
          </div>
        </div>

        {/* EOD Action Card */}
        <div className="rounded-2xl border border-slate-200 bg-white p-5 shadow-xs flex flex-col justify-between">
          <div className="flex items-center space-x-2 text-slate-500 text-xs font-semibold uppercase">
            <Clock className="h-4 w-4 text-indigo-600" />
            <span>Execute EOD Pipeline</span>
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
                  <span>Executing EOD Batch...</span>
                </>
              ) : (
                <>
                  <Play className="h-3.5 w-3.5" />
                  <span>Run 8-Step EOD Batch</span>
                </>
              )}
            </button>
            {!eodStatus?.cutoffClosed && (
              <p className="mt-1.5 text-center text-[10px] text-amber-600 font-medium">
                * Cutoff required before executing EOD
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
              End-of-Day Processing Pipeline (8-Step Atomic Batch)
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
                  <span className="font-mono text-[11px] font-bold">STEP {s.step}</span>
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

      {/* General Ledger Trial Balance Viewer */}
      <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs space-y-4">
        <div className="flex flex-wrap items-center justify-between border-b border-slate-100 pb-3 gap-3">
          <div className="flex items-center space-x-2">
            <Scale className="h-5 w-5 text-indigo-700" />
            <div>
              <h3 className="text-sm font-bold uppercase tracking-wider text-slate-800">
                General Ledger Trial Balance
              </h3>
              <p className="text-xs text-slate-500">
                Proof of Accounting Equilibrium Invariant 1: Total Debits = Total Credits
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
                  <span>INVARIANT 1: EQUILIBRIUM BALANCED (Diff = 0 VND)</span>
                </>
              ) : (
                <>
                  <AlertTriangle className="h-4 w-4 text-red-600" />
                  <span>WARNING: UNBALANCED GL ENTRIES</span>
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
                  <th className="px-3 py-2.5">GL Code</th>
                  <th className="px-3 py-2.5">GL Account Name</th>
                  <th className="px-3 py-2.5 text-right">Total Debits</th>
                  <th className="px-3 py-2.5 text-right">Total Credits</th>
                  <th className="px-3 py-2.5 text-right">Net Balance</th>
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
                    TOTAL BANK EQUILIBRIUM:
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
            No transaction entries recorded for this working date.
          </div>
        )}
      </div>
    </div>
  );
}

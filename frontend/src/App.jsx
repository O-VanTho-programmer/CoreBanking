import React, { useState, useEffect } from 'react';
import Header from './components/common/Header';
import WorkspaceTabs from './components/workspace/WorkspaceTabs';
import TabFormContainer from './components/workspace/TabFormContainer';
import EodAccountingCockpit from './pages/EodAccountingCockpit';
import { eodApi } from './services/eodApi';
import { formApi } from './services/formApi';
import { accountApi } from './services/accountApi';
import {
  Landmark,
  ArrowLeftRight,
  BarChart3,
  ShieldCheck,
  Search,
  Building,
  Loader2,
  ExternalLink,
  Layers,
} from 'lucide-react';

export default function App() {
  const [activeView, setActiveView] = useState('workspace'); // 'workspace' | 'cockpit'
  const [workingDate, setWorkingDate] = useState('2026-11-05');
  const [isCutoff, setIsCutoff] = useState(false);
  const [shortcuts, setShortcuts] = useState([]);

  // Workplace Multi-Tab State
  const [openTabs, setOpenTabs] = useState([
    {
      id: 'tab-default',
      formCode: 'FORM_CASH_DEP',
      title: 'Cash Deposit',
      initialParams: {},
      isDirty: false,
    },
  ]);
  const [activeTabId, setActiveTabId] = useState('tab-default');

  // Quick Account Inspector Side Panel State
  const [inspectorAccount, setInspectorAccount] = useState('');
  const [inspectorData, setInspectorData] = useState(null);
  const [isInspecting, setIsInspecting] = useState(false);
  const [inspectorError, setInspectorError] = useState(null);

  // Poll system status (Working date & Cutoff status)
  const fetchSystemStatus = async () => {
    try {
      const status = await eodApi.getStatus();
      if (status?.workingDate) setWorkingDate(status.workingDate);
      if (status?.cutoffClosed !== undefined) setIsCutoff(status.cutoffClosed);
    } catch (err) {
      console.warn('System status poll:', err.message);
    }
  };

  // Load shortcuts catalog on initial mount
  const fetchShortcuts = async () => {
    try {
      const list = await formApi.getShortcuts();
      setShortcuts(list || []);
    } catch (err) {
      console.warn('Failed to load shortcuts:', err.message);
    }
  };

  useEffect(() => {
    fetchSystemStatus();
    fetchShortcuts();
  }, []);

  // Open Form into Workplace Tabs (enforcing max 8 tabs limit)
  const handleOpenForm = (formCode, formName, initialParams = {}) => {
    setActiveView('workspace');

    // Check maximum 8 tabs limit
    if (openTabs.length >= 8) {
      alert('Maximum limit of 8 workspace tabs reached. Please close an existing tab before opening a new one.');
      return;
    }

    const title = formName || formCode;
    const newTabId = `tab-${Date.now()}-${Math.random().toString(36).substring(2, 6)}`;
    const newTab = {
      id: newTabId,
      formCode,
      title,
      initialParams,
      isDirty: false,
    };

    setOpenTabs((prev) => [...prev, newTab]);
    setActiveTabId(newTabId);
  };

  // Form Navigation callback (invoked by NAVIGATE_FORM action)
  const handleNavigate = (targetFormCode, passData = {}) => {
    const matched = shortcuts.find((s) => s.formCode === targetFormCode);
    const title = matched ? matched.formName : targetFormCode;
    handleOpenForm(targetFormCode, title, passData);
  };

  // Close specific tab
  const handleCloseTab = (tabIdToClose) => {
    if (openTabs.length <= 1) return; // Keep at least one tab open

    const tabIndex = openTabs.findIndex((t) => t.id === tabIdToClose);
    const remaining = openTabs.filter((t) => t.id !== tabIdToClose);

    if (activeTabId === tabIdToClose) {
      const nextActive = remaining[Math.min(tabIndex, remaining.length - 1)];
      setActiveTabId(nextActive.id);
    }

    setOpenTabs(remaining);
  };

  // Close all other tabs
  const handleCloseOtherTabs = () => {
    const current = openTabs.find((t) => t.id === activeTabId);
    if (current) {
      setOpenTabs([current]);
    }
  };

  // Close all tabs and reset to default
  const handleCloseAllTabs = () => {
    const defaultTab = {
      id: `tab-${Date.now()}`,
      formCode: 'FORM_CASH_DEP',
      title: 'Cash Deposit',
      initialParams: {},
      isDirty: false,
    };
    setOpenTabs([defaultTab]);
    setActiveTabId(defaultTab.id);
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
    return new Intl.NumberFormat('en-US').format(Number(val));
  };

  return (
    <div className="min-h-screen bg-slate-100/70 text-slate-800 flex flex-col font-sans">
      {/* Banking Top Header with Spotlight Search & Mega Menu */}
      <Header
        workingDate={workingDate}
        isCutoff={isCutoff}
        branchCode="001 - Head Office HCM"
        tellerId="TELLER01"
        shortcuts={shortcuts}
        onOpenForm={handleOpenForm}
      />

      {/* Main Container */}
      <main className="mx-auto flex-1 w-full max-w-7xl px-4 py-6 sm:px-6 space-y-6">
        {/* Workspace Mode Navigation Bar */}
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-2 rounded-2xl shadow-xs">
          <nav className="flex space-x-1 sm:space-x-4">
            <button
              onClick={() => setActiveView('workspace')}
              className={`flex items-center space-x-2 border-b-2 px-3 py-3 text-xs sm:text-sm font-bold transition ${
                activeView === 'workspace'
                  ? 'border-blue-700 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              <ArrowLeftRight className="h-4 w-4" />
              <span>TELLER & OPERATIONS WORKSPACE</span>
              <span className="rounded-full bg-blue-100 px-2 py-0.5 text-[10px] font-bold text-blue-800">
                {openTabs.length} / 8 Tabs
              </span>
            </button>

            <button
              onClick={() => setActiveView('cockpit')}
              className={`flex items-center space-x-2 border-b-2 px-3 py-3 text-xs sm:text-sm font-bold transition ${
                activeView === 'cockpit'
                  ? 'border-blue-700 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              <BarChart3 className="h-4 w-4" />
              <span>EOD & ACCOUNTING COCKPIT</span>
            </button>
          </nav>

          <div className="hidden md:flex items-center space-x-2 text-xs font-medium text-slate-400">
            <ShieldCheck className="h-4 w-4 text-emerald-600" />
            <span>Server-Driven Form Engine v2.0 • Multi-Tab</span>
          </div>
        </div>

        {/* View Canvas: Workplace Tabs or Cockpit */}
        {activeView === 'workspace' ? (
          <div className="grid grid-cols-1 gap-6 lg:grid-cols-3">
            {/* Left 2 Cols: Multi-Tab Workspace Canvas */}
            <div className="lg:col-span-2 space-y-4">
              {/* Tab Strip */}
              <WorkspaceTabs
                tabs={openTabs}
                activeTabId={activeTabId}
                onSelectTab={setActiveTabId}
                onCloseTab={handleCloseTab}
                onCloseOtherTabs={handleCloseOtherTabs}
                onCloseAllTabs={handleCloseAllTabs}
                onNewTab={() => handleOpenForm('FORM_CASH_DEP', 'Cash Deposit')}
              />

              {/* Tab Containers: Keep rendered to isolate and preserve state */}
              {openTabs.map((tab) => (
                <div key={tab.id} className={tab.id === activeTabId ? 'block' : 'hidden'}>
                  <TabFormContainer
                    tab={tab}
                    onNavigate={handleNavigate}
                    onTransactionSuccess={fetchSystemStatus}
                  />
                </div>
              ))}
            </div>

            {/* Right 1 Col: Quick Account Inspector & Teller Drawer */}
            <div className="space-y-6">
              {/* Live Account Lookup */}
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

              {/* Teller Cash Drawer Summary */}
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
                    <span className="text-slate-500">Instant Limit:</span>
                    <span className="font-mono font-bold text-slate-900">500,000,000 VND</span>
                  </div>
                </div>
              </div>

              {/* Quick Transaction Shortcuts Card */}
              <div className="rounded-2xl border border-slate-200 bg-white p-6 shadow-xs">
                <div className="flex items-center space-x-2 border-b border-slate-100 pb-3">
                  <Layers className="h-4 w-4 text-indigo-600" />
                  <h3 className="text-xs font-bold uppercase tracking-wider text-slate-700">
                    Quick Operations
                  </h3>
                </div>
                <div className="mt-3 space-y-1.5">
                  {shortcuts.slice(0, 5).map((sc) => (
                    <button
                      key={sc.shortcutCode}
                      type="button"
                      onClick={() => handleOpenForm(sc.formCode, sc.formName)}
                      className="w-full flex items-center justify-between p-2 rounded-lg text-left text-xs text-slate-700 hover:bg-slate-50 hover:text-blue-700 transition group"
                    >
                      <span className="font-medium truncate max-w-[200px]">{sc.formName}</span>
                      <ExternalLink className="h-3 w-3 text-slate-400 group-hover:text-blue-600 shrink-0" />
                    </button>
                  ))}
                </div>
              </div>
            </div>
          </div>
        ) : (
          <div className="animate-fadeIn">
            <EodAccountingCockpit
              onSystemDateChange={(newDate) => {
                setWorkingDate(newDate);
                setIsCutoff(false);
              }}
            />
          </div>
        )}
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-3 text-center text-xs text-slate-400">
        CBS Core Banking Back-Office Portal • Oracle 19c • Spring Boot 3.3.4 • Java 21 LTS
      </footer>
    </div>
  );
}

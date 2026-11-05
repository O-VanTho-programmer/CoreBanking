import React, { useState, useEffect } from 'react';
import Header from './components/common/Header';
import TellerWorkspace from './pages/TellerWorkspace';
import LendingConsole from './pages/LendingConsole';
import EodAccountingCockpit from './pages/EodAccountingCockpit';
import { eodApi } from './services/eodApi';
import { Landmark, ArrowLeftRight, FileText, BarChart3, ShieldCheck } from 'lucide-react';

export default function App() {
  const [activeTab, setActiveTab] = useState('teller'); // 'teller' | 'lending' | 'eod'
  const [workingDate, setWorkingDate] = useState('2026-11-05');
  const [isCutoff, setIsCutoff] = useState(false);

  const fetchSystemStatus = async () => {
    try {
      const status = await eodApi.getStatus();
      if (status?.workingDate) setWorkingDate(status.workingDate);
      if (status?.cutoffClosed !== undefined) setIsCutoff(status.cutoffClosed);
    } catch (err) {
      console.warn('System status poll:', err.message);
    }
  };

  useEffect(() => {
    fetchSystemStatus();
  }, []);

  return (
    <div className="min-h-screen bg-slate-100/70 text-slate-800 flex flex-col font-sans">
      {/* Banking Top Header */}
      <Header
        workingDate={workingDate}
        isCutoff={isCutoff}
        branchCode="001 - Head Office HCM"
        tellerId="TELLER01"
      />

      {/* Main Container */}
      <main className="mx-auto flex-1 w-full max-w-7xl px-4 py-6 sm:px-6 space-y-6">
        {/* Workspace Navigation Bar */}
        <div className="flex items-center justify-between border-b border-slate-200 bg-white px-6 py-2 rounded-2xl shadow-xs">
          <nav className="flex space-x-1 sm:space-x-4">
            <button
              onClick={() => setActiveTab('teller')}
              className={`flex items-center space-x-2 border-b-2 px-3 py-3 text-xs sm:text-sm font-bold transition ${
                activeTab === 'teller'
                  ? 'border-blue-700 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              <ArrowLeftRight className="h-4 w-4" />
              <span>GIAO DỊCH QUẦY (TELLER)</span>
            </button>

            <button
              onClick={() => setActiveTab('lending')}
              className={`flex items-center space-x-2 border-b-2 px-3 py-3 text-xs sm:text-sm font-bold transition ${
                activeTab === 'lending'
                  ? 'border-blue-700 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              <FileText className="h-4 w-4" />
              <span>TÍN DỤNG & CHO VAY (LENDING)</span>
            </button>

            <button
              onClick={() => setActiveTab('eod')}
              className={`flex items-center space-x-2 border-b-2 px-3 py-3 text-xs sm:text-sm font-bold transition ${
                activeTab === 'eod'
                  ? 'border-blue-700 text-blue-700'
                  : 'border-transparent text-slate-500 hover:text-slate-900'
              }`}
            >
              <BarChart3 className="h-4 w-4" />
              <span>VẬN HÀNH & KẾ TOÁN (EOD COCKPIT)</span>
            </button>
          </nav>

          <div className="hidden md:flex items-center space-x-2 text-xs font-medium text-slate-400">
            <ShieldCheck className="h-4 w-4 text-emerald-600" />
            <span>Server-Driven Form Engine v1.0</span>
          </div>
        </div>

        {/* Tab Content Canvas */}
        <div className="animate-fadeIn">
          {activeTab === 'teller' && (
            <TellerWorkspace onTransactionSuccess={fetchSystemStatus} />
          )}
          {activeTab === 'lending' && (
            <LendingConsole onTransactionSuccess={fetchSystemStatus} />
          )}
          {activeTab === 'eod' && (
            <EodAccountingCockpit
              onSystemDateChange={(newDate) => {
                setWorkingDate(newDate);
                setIsCutoff(false);
              }}
            />
          )}
        </div>
      </main>

      {/* Footer */}
      <footer className="border-t border-slate-200 bg-white py-3 text-center text-xs text-slate-400">
        CBS Core Banking Back-Office Portal • Oracle 19c • Spring Boot 3.3.4 • Java 21 LTS
      </footer>
    </div>
  );
}

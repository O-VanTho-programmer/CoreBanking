import React from 'react';
import { Landmark, Calendar, Clock, User, Shield, AlertCircle, CheckCircle2 } from 'lucide-react';

export default function Header({
  workingDate = '2026-11-05',
  isCutoff = false,
  branchCode = '001 - Head Office',
  tellerId = 'TELLER01',
}) {
  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur-md shadow-xs">
      <div className="mx-auto flex max-w-7xl items-center justify-between px-4 py-3 sm:px-6">
        {/* Brand & CBS Identity */}
        <div className="flex items-center space-x-3">
          <div className="flex h-10 w-10 items-center justify-center rounded-xl bg-gradient-to-tr from-blue-900 to-indigo-700 text-white shadow-md shadow-blue-950/20">
            <Landmark className="h-5 w-5" />
          </div>
          <div>
            <div className="flex items-center space-x-2">
              <h1 className="text-base font-extrabold tracking-tight text-slate-900">
                CORE BANKING SYSTEM
              </h1>
              <span className="rounded bg-blue-100 px-1.5 py-0.5 text-[10px] font-bold text-blue-800">
                PROD v2.0
              </span>
            </div>
            <p className="text-[11px] text-slate-500 font-medium">
              Operational Management & Teller Portal (Back-Office)
            </p>
          </div>
        </div>

        {/* Operational Status Info */}
        <div className="flex items-center space-x-4">
          {/* Working Date */}
          <div className="hidden sm:flex items-center space-x-2 rounded-lg border border-slate-200 bg-slate-50/80 px-3 py-1.5 text-xs">
            <Calendar className="h-4 w-4 text-slate-500" />
            <span className="text-slate-500">Working Date:</span>
            <span className="font-mono font-bold text-slate-900">{workingDate}</span>
          </div>

          {/* Cutoff Status Badge */}
          <div
            className={`flex items-center space-x-1.5 rounded-full px-3 py-1 text-xs font-semibold ${
              isCutoff
                ? 'bg-amber-100 text-amber-900 border border-amber-200'
                : 'bg-emerald-100 text-emerald-900 border border-emerald-200'
            }`}
          >
            {isCutoff ? (
              <>
                <AlertCircle className="h-3.5 w-3.5 text-amber-600 animate-pulse" />
                <span>CUTOFF (Closed)</span>
              </>
            ) : (
              <>
                <CheckCircle2 className="h-3.5 w-3.5 text-emerald-600" />
                <span>ONLINE (Open)</span>
              </>
            )}
          </div>

          {/* Teller Info */}
          <div className="flex items-center space-x-2 border-l border-slate-200 pl-4">
            <div className="flex h-8 w-8 items-center justify-center rounded-full bg-slate-100 text-slate-600 border border-slate-200">
              <User className="h-4 w-4" />
            </div>
            <div className="text-left text-xs leading-tight">
              <div className="font-bold text-slate-800">{tellerId}</div>
              <div className="text-[10px] text-slate-500">{branchCode}</div>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}

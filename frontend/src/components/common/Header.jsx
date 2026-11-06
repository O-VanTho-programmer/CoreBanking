import React, { useState, useEffect, useRef } from 'react';
import {
  Landmark,
  Calendar,
  User,
  AlertCircle,
  CheckCircle2,
  Menu,
  X,
  Search,
  Layers,
  ArrowDownLeft,
  ArrowUpRight,
  ArrowLeftRight,
  CreditCard,
  FileText,
  UserPlus,
  ChevronRight,
  ArrowLeft,
  Banknote,
} from 'lucide-react';

// Structured Hierarchical Modules Menu Definition
const MODULES_MENU = [
  {
    id: 'deposit',
    name: 'Deposit',
    code: 'DPT',
    description: 'Account inquiry & product catalogue',
    children: [
      {
        formCode: 'FORM_DPT_INFO',
        title: 'Account Information',
        description: 'Inquire CASA balance, ledger & statement',
      },
      {
        formCode: 'FORM_DPT_CATALOGUE',
        title: 'Catalogue Definition',
        description: 'Configure deposit product interest & GL',
      },
    ],
  },
  {
    id: 'cash',
    name: 'Cash',
    code: 'CSH',
    description: 'Teller counter cash transactions',
    children: [
      {
        formCode: 'FORM_CASH_DEP',
        title: 'Cash Deposit',
        description: 'Over-the-counter physical cash deposit',
      },
      {
        formCode: 'FORM_CASH_WDR',
        title: 'Cash Withdrawal',
        description: 'Over-the-counter physical cash withdrawal',
      },
      {
        formCode: 'FORM_TRF_INTERNAL',
        title: 'Internal Transfer',
        description: 'Account-to-account transfer with fee',
      },
    ],
  },
  {
    id: 'credit',
    name: 'Credit',
    code: 'CRD',
    description: 'Loan account, origination & catalogue',
    children: [
      {
        formCode: 'FORM_CRD_INFO',
        title: 'Account Information',
        description: 'Inquire loan contracts, balance & repayment schedule',
      },
      {
        formCode: 'FORM_CREDIT_ACCOUNT_OPEN',
        title: 'Open Credit Account',
        description: 'Originate, calculate EMI & approve loan contracts',
      },
      {
        formCode: 'FORM_CRD_CATALOGUE',
        title: 'Catalogue Definition',
        description: 'Configure credit product packages & interest margins',
      },
    ],
  },
  {
    id: 'customer',
    name: 'Customer',
    code: 'CUST',
    description: 'Customer profile, CIF 360 & onboarding',
    children: [
      {
        formCode: 'FORM_CUST_PROFILE',
        title: 'Customer Profile',
        description: 'CIF 360 inquiry & profile actions',
      },
      {
        formCode: 'FORM_CUST_CREATE',
        title: 'Register Customer',
        description: 'Onboard new individual or corporate CIF profile',
      },
    ],
  },
];

export default function Header({
  workingDate = '2026-11-05',
  isCutoff = false,
  branchCode = '001 - Head Office',
  tellerId = 'TELLER01',
  shortcuts = [],
  onOpenForm,
}) {
  const [isMenuOpen, setIsMenuOpen] = useState(false);
  const [activeModuleId, setActiveModuleId] = useState(null); // null = parent menu, string = selected module
  const [searchQuery, setSearchQuery] = useState('');
  const [isSearchOpen, setIsSearchOpen] = useState(false);
  const searchInputRef = useRef(null);
  const menuRef = useRef(null);
  const searchRef = useRef(null);

  // Global Ctrl + K shortcut
  useEffect(() => {
    const handleKeyDown = (e) => {
      if ((e.ctrlKey || e.metaKey) && e.key === 'k') {
        e.preventDefault();
        searchInputRef.current?.focus();
        setIsSearchOpen(true);
      } else if (e.key === 'Escape') {
        setIsSearchOpen(false);
        setIsMenuOpen(false);
        setActiveModuleId(null);
      }
    };
    window.addEventListener('keydown', handleKeyDown);
    return () => window.removeEventListener('keydown', handleKeyDown);
  }, []);

  // Close menus on outside click
  useEffect(() => {
    const handleClickOutside = (e) => {
      if (menuRef.current && !menuRef.current.contains(e.target)) {
        setIsMenuOpen(false);
        setActiveModuleId(null);
      }
      if (searchRef.current && !searchRef.current.contains(e.target)) {
        setIsSearchOpen(false);
      }
    };
    document.addEventListener('mousedown', handleClickOutside);
    return () => document.removeEventListener('mousedown', handleClickOutside);
  }, []);

  // Filter shortcuts matching search query
  const filteredShortcuts = searchQuery.trim()
    ? shortcuts.filter((sc) => {
      const q = searchQuery.toLowerCase();
      return (
        sc.formName.toLowerCase().includes(q) ||
        sc.keywords.toLowerCase().includes(q) ||
        sc.formCode.toLowerCase().includes(q) ||
        sc.module.toLowerCase().includes(q)
      );
    })
    : shortcuts.slice(0, 6);

  const handleSelectShortcut = (shortcut) => {
    if (onOpenForm) {
      onOpenForm(shortcut.formCode, shortcut.formName);
    }
    setSearchQuery('');
    setIsSearchOpen(false);
  };

  const handleOpenNamedForm = (formCode, formName) => {
    if (onOpenForm) {
      onOpenForm(formCode, formName);
    }
    setIsMenuOpen(false);
    setActiveModuleId(null);
  };

  // Find currently active selected module object
  const selectedModuleObj = MODULES_MENU.find((m) => m.id === activeModuleId);

  return (
    <header className="sticky top-0 z-40 border-b border-slate-200 bg-white/95 backdrop-blur-md shadow-xs">
      <div className="mx-auto flex min-w-4xl w-full max-w-full items-center justify-between px-4 py-2.5 sm:px-6 gap-3">
        <div className="flex items-center space-x-3">
          <div className="relative" ref={menuRef}>
            <button
              type="button"
              onClick={() => {
                if (isMenuOpen) {
                  setIsMenuOpen(false);
                  setActiveModuleId(null);
                } else {
                  setIsMenuOpen(true);
                  setActiveModuleId(null);
                }
              }}
              className={`flex h-10 w-10 items-center justify-center rounded-xl border transition ${isMenuOpen
                ? 'border-blue-600 bg-blue-50 text-blue-700'
                : 'border-slate-200 bg-slate-50 text-slate-700 hover:bg-slate-100 hover:text-slate-900'
                }`}
              title="Modules & Services Menu"
            >
              {isMenuOpen ? <X className="h-5 w-5" /> : <Menu className="h-5 w-5" />}
            </button>

            {/* Hierarchical Drill-Down Dropdown Menu */}
            {isMenuOpen && (
              <div className="absolute left-0 mt-2 min-w-[300px] w-full rounded-lg border border-slate-200 bg-white shadow-2xl animate-fadeIn z-50">
                {/* Dropdown Header: Parent title OR Back button */}
                {activeModuleId ? (
                  <div className="flex items-center justify-between border-b border-slate-100 pb-2 pt-2 px-1">
                    <button
                      type="button"
                      onClick={() => setActiveModuleId(null)}
                      className="flex items-center space-x-1.5 text-xs font-bold text-slate-600 hover:text-blue-700 transition group"
                    >
                      <ArrowLeft className="h-4 w-4 text-slate-500 group-hover:text-blue-600 group-hover:-translate-x-0.5 transition" />
                      <span>Back</span>
                    </button>
                  </div>
                ) : (
                  <div className=""></div>
                )}

                {/* Dropdown Content Area */}
                <div className="mt-2 text-xs">
                  {activeModuleId && selectedModuleObj ? (
                    /* CHILDREN MENU */
                    <div className="space-y-1.5 animate-fadeIn">
                      <div className="px-2 py-1 text-[11px] font-bold text-slate-400 uppercase tracking-wider flex items-center space-x-1.5">
                        <span>{selectedModuleObj.name} Services</span>
                      </div>

                      <div className="space-y-1">
                        {selectedModuleObj.children.map((child) => (
                          <button
                            key={child.formCode}
                            type="button"
                            onClick={() => handleOpenNamedForm(child.formCode, child.title)}
                            className="w-full text-left p-2.5 hover:bg-blue-50/70 transition flex items-center justify-between group border border-transparent hover:border-blue-100"
                          >
                            <div className="space-y-0.5">
                              <div className="font-semibold text-slate-800 text-xs group-hover:text-blue-900 flex items-center space-x-2">
                                <span>{child.title}</span>
                                <span className={`rounded px-1.5 py-0.2 text-[9px] font-bold ${child.badgeColor}`}>
                                  {child.badge}
                                </span>
                              </div>
                            </div>

                            <ChevronRight className="h-4 w-4 text-slate-300 group-hover:text-blue-600 shrink-0 ml-2 transition group-hover:translate-x-0.5" />
                          </button>
                        ))}
                      </div>
                    </div>
                  ) : (
                    /* PARENT MENU */
                    <div className="space-y-1 animate-fadeIn">
                      {MODULES_MENU.map((mod) => {
                        return (
                          <button
                            key={mod.id}
                            type="button"
                            onClick={() => setActiveModuleId(mod.id)}
                            className="w-full text-left p-2.5 hover:bg-blue-50/70 transition flex items-center justify-between group border border-transparent hover:border-blue-100"
                          >
                            <div className="font-bold text-slate-800 text-xs group-hover:text-blue-900 flex items-center space-x-1.5">
                              <span>{mod.name}</span>
                            </div>

                            <ChevronRight className="h-4 w-4 text-slate-300 group-hover:text-blue-600 shrink-0 ml-2 transition group-hover:translate-x-0.5" />
                          </button>
                        );
                      })}
                    </div>
                  )}
                </div>
              </div>
            )}
          </div>

          {/* Core Banking Logo */}
          <div className="flex items-center space-x-2.5">
            <div className="flex h-9 w-9 items-center justify-center rounded-xl bg-gradient-to-tr from-blue-900 to-indigo-700 text-white shadow-md shadow-blue-950/20">
              <Landmark className="h-4.5 w-4.5" />
            </div>
            <div className="hidden sm:block">
              <div className="flex items-center space-x-1.5">
                <h1 className="text-sm font-black tracking-tight text-slate-900">
                  CBS CORE
                </h1>
                <span className="rounded bg-blue-100 px-1.5 py-0.2 text-[9px] font-bold text-blue-800">
                  v2.0
                </span>
              </div>
              <p className="text-[10px] text-slate-400 font-medium">Terminal Portal</p>
            </div>
          </div>
        </div>

        {/* Center Section: Spotlight Search Bar (Command Palette) */}
        <div className="relative flex-1 max-w-md mx-2" ref={searchRef}>
          <div className="relative">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
              <Search className="h-4 w-4 text-slate-400" />
            </div>
            <input
              ref={searchInputRef}
              type="text"
              value={searchQuery}
              onChange={(e) => {
                setSearchQuery(e.target.value);
                setIsSearchOpen(true);
              }}
              onFocus={() => setIsSearchOpen(true)}
              placeholder="Search FO transactions, BO catalogues (e.g. cash, deposit, loan)..."
              className="w-full rounded-xl border border-slate-300 bg-slate-50 py-1.5 pl-9 pr-14 text-xs font-medium text-slate-800 placeholder-slate-400 transition focus:border-blue-600 focus:bg-white focus:outline-none focus:ring-2 focus:ring-blue-100"
            />
            <div className="pointer-events-none absolute inset-y-0 right-0 flex items-center pr-2">
              <kbd className="rounded border border-slate-200 bg-white px-1.5 py-0.5 text-[9px] font-mono text-slate-400 shadow-2xs">
                Ctrl K
              </kbd>
            </div>
          </div>

          {/* Spotlight Dropdown Results */}
          {isSearchOpen && (
            <div className="absolute left-0 mt-2 w-full rounded-2xl border border-slate-200 bg-white p-2 shadow-2xl animate-fadeIn z-50">
              <div className="px-2 py-1 text-[10px] font-bold uppercase tracking-wider text-slate-400 flex items-center justify-between">
                <span>{searchQuery.trim() ? 'Matching Shortcuts' : 'Quick Actions'}</span>
                <span className="text-[9px] font-mono">{filteredShortcuts.length} results</span>
              </div>

              <div className="mt-1 max-h-64 overflow-y-auto divide-y divide-slate-100">
                {filteredShortcuts.length > 0 ? (
                  filteredShortcuts.map((sc) => (
                    <button
                      key={sc.shortcutCode}
                      type="button"
                      onClick={() => handleSelectShortcut(sc)}
                      className="w-full text-left rounded-lg p-2 hover:bg-blue-50/70 transition flex items-center justify-between group"
                    >
                      <div className="flex items-center space-x-2.5">
                        <div className="flex h-7 w-7 items-center justify-center rounded-lg bg-slate-100 text-slate-600 group-hover:bg-blue-600 group-hover:text-white transition">
                          <FileText className="h-3.5 w-3.5" />
                        </div>
                        <div>
                          <div className="font-semibold text-slate-800 text-xs group-hover:text-blue-900">
                            {sc.formName}
                          </div>
                          <div className="text-[10px] font-mono text-slate-400">
                            {sc.formCode}
                          </div>
                        </div>
                      </div>

                      <div className="flex items-center space-x-1.5">
                        <span
                          className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${sc.category === 'FO_TRANSACTION'
                            ? 'bg-emerald-100 text-emerald-800'
                            : sc.category === 'BO_CATALOGUE'
                              ? 'bg-amber-100 text-amber-800'
                              : 'bg-blue-100 text-blue-800'
                            }`}
                        >
                          {sc.category === 'FO_TRANSACTION' ? 'FO' : sc.category === 'BO_CATALOGUE' ? 'BO' : 'INQ'}
                        </span>
                        <span className="text-[9px] font-mono text-slate-400 font-semibold">
                          {sc.module}
                        </span>
                      </div>
                    </button>
                  ))
                ) : (
                  <div className="p-4 text-center text-xs text-slate-400">
                    No matching forms found for "{searchQuery}"
                  </div>
                )}
              </div>
            </div>
          )}
        </div>

        {/* Right Section: Working Date, Cutoff & Teller */}
        <div className="flex items-center space-x-3 shrink-0">
          {/* Working Date */}
          <div className="hidden md:flex items-center space-x-1.5 rounded-lg border border-slate-200 bg-slate-50/80 px-2.5 py-1 text-xs">
            <Calendar className="h-3.5 w-3.5 text-slate-500" />
            <span className="text-slate-400">Date:</span>
            <span className="font-mono font-bold text-slate-800">{workingDate}</span>
          </div>

          {/* Cutoff Badge */}
          <div
            className={`flex items-center space-x-1 rounded-full px-2.5 py-0.5 text-[11px] font-semibold ${isCutoff
              ? 'bg-amber-100 text-amber-900 border border-amber-200'
              : 'bg-emerald-100 text-emerald-900 border border-emerald-200'
              }`}
          >
            {isCutoff ? (
              <>
                <AlertCircle className="h-3 w-3 text-amber-600 animate-pulse" />
                <span>CUTOFF</span>
              </>
            ) : (
              <>
                <CheckCircle2 className="h-3 w-3 text-emerald-600" />
                <span>ONLINE</span>
              </>
            )}
          </div>

          {/* User Profile */}
          <div className="flex items-center space-x-2 border-l border-slate-200 pl-3">
            <div className="flex h-7 w-7 items-center justify-center rounded-full bg-slate-100 text-slate-600 border border-slate-200">
              <User className="h-3.5 w-3.5" />
            </div>
            <div className="hidden lg:block text-left text-xs leading-tight">
              <div className="font-bold text-slate-800">{tellerId}</div>
              <div className="text-[9px] text-slate-400">{branchCode}</div>
            </div>
          </div>
        </div>
      </div>
    </header>
  );
}

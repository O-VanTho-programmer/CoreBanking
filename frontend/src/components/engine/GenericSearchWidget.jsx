import React, { useState, useEffect, useCallback } from 'react';
import apiClient from '../../services/apiClient';
import {
  Search,
  Filter,
  ChevronDown,
  ChevronUp,
  RotateCcw,
  Loader2,
  Phone,
  Mail,
  MapPin,
  FileQuestion,
} from 'lucide-react';

export default function GenericSearchWidget({
  schema,
  onNavigate,
  modalMode = false,
  onSelectRecord = null,
}) {
  const [keyword, setKeyword] = useState('');
  const [filters, setFilters] = useState({});
  const [isAdvanceOpen, setIsAdvanceOpen] = useState(false);
  const [isLoading, setIsLoading] = useState(false);
  const [records, setRecords] = useState([]);
  const [optionsMap, setOptionsMap] = useState({});
  const [hasSearched, setHasSearched] = useState(false);

  // Initialize filter state & load options for select fields with optionsEndpoint
  useEffect(() => {
    if (!schema) return;

    const initialFilters = {};
    (schema.advanceFilters || []).forEach((f) => {
      initialFilters[f.name] = '';
    });
    setFilters(initialFilters);
    setKeyword('');

    // Load async options
    async function fetchDynamicOptions() {
      const endpointsToFetch = (schema.advanceFilters || []).filter(
        (f) => f.type === 'SELECT' && f.optionsEndpoint
      );

      for (const f of endpointsToFetch) {
        try {
          const res = await apiClient.get(f.optionsEndpoint);
          const data = res.data?.value || res.data || [];
          setOptionsMap((prev) => ({
            ...prev,
            [f.name]: data.map((item) => {
              if (typeof item === 'string') return { label: item, value: item };
              return {
                label: item[f.optionLabel || 'label'] || item.name || item.code,
                value: item[f.optionValue || 'value'] || item.code || item.id,
              };
            }),
          }));
        } catch (err) {
          console.warn(`Failed to fetch options for filter [${f.name}]:`, err.message);
        }
      }
    }

    fetchDynamicOptions();
  }, [schema]);

  // Execute search query
  const executeSearch = useCallback(
    async (overrideFilters = null, overrideKeyword = null) => {
      if (!schema?.searchEndpoint) return;

      try {
        setIsLoading(true);
        const activeFilters = overrideFilters !== null ? overrideFilters : filters;
        const activeKeyword = overrideKeyword !== null ? overrideKeyword : keyword;

        const params = {};
        const paramName = schema.quickSearch?.paramName || 'keyword';
        if (activeKeyword.trim()) {
          params[paramName] = activeKeyword.trim();
        }

        Object.entries(activeFilters).forEach(([key, val]) => {
          if (val !== undefined && val !== null && String(val).trim() !== '') {
            params[key] = String(val).trim();
          }
        });

        const res = await apiClient.get(schema.searchEndpoint, { params });
        const list = Array.isArray(res.data)
          ? res.data
          : res.data?.content || res.data?.data || res.data?.value || [];
        setRecords(list);
        setHasSearched(true);
      } catch (err) {
        console.error('Search query error:', err.message);
      } finally {
        setIsLoading(false);
      }
    },
    [schema, filters, keyword]
  );

  // Run initial search when schema changes
  useEffect(() => {
    if (schema?.searchEndpoint) {
      executeSearch();
    }
  }, [schema?.searchEndpoint]); // eslint-disable-line react-hooks/exhaustive-deps

  const handleFilterChange = (name, val) => {
    setFilters((prev) => ({ ...prev, [name]: val }));
  };

  const handleClearFilters = () => {
    const cleared = {};
    (schema.advanceFilters || []).forEach((f) => {
      cleared[f.name] = '';
    });
    setFilters(cleared);
    setKeyword('');
    executeSearch(cleared, '');
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      executeSearch();
    }
  };

  const activeFilterCount = Object.values(filters).filter((v) => v !== '').length;

  // Render individual cell based on column type
  const renderCell = (record, col) => {
    const val = record[col.key];

    switch (col.type) {
      case 'MONO_BOLD':
        return (
          <span className="font-mono font-bold text-blue-700">
            {val || '-'}
          </span>
        );

      case 'NAME_WITH_SUB': {
        const subVal = col.subKey ? record[col.subKey] : null;
        return (
          <div>
            <div className="font-semibold text-slate-900">{val || '-'}</div>
            {subVal && (
              <div className="text-[10px] text-slate-400 capitalize">
                {String(subVal).toLowerCase()}
              </div>
            )}
          </div>
        );
      }

      case 'BADGE':
        return (
          <span className="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-700">
            {val || '-'}
          </span>
        );

      case 'ID_WITH_TAX': {
        const taxVal = col.subKey ? record[col.subKey] : null;
        return (
          <div className="font-mono text-[11px]">
            <div>{val || '-'}</div>
            {taxVal && <div className="text-[10px] text-slate-400">Tax: {taxVal}</div>}
          </div>
        );
      }

      case 'CONTACT': {
        const emailVal = col.subKey ? record[col.subKey] : null;
        return (
          <div className="text-[11px] space-y-0.5">
            {val ? (
              <div className="flex items-center space-x-1 text-slate-700">
                <Phone className="h-3 w-3 text-slate-400 shrink-0" />
                <span>{val}</span>
              </div>
            ) : (
              <span className="text-slate-300">-</span>
            )}
            {emailVal && (
              <div className="flex items-center space-x-1 text-slate-500 text-[10px]">
                <Mail className="h-2.5 w-2.5 text-slate-400 shrink-0" />
                <span className="truncate max-w-[120px]">{emailVal}</span>
              </div>
            )}
          </div>
        );
      }

      case 'ADDRESS': {
        const keys = col.addressKeys || ['streetAddress', 'district', 'province'];
        const parts = keys.map((k) => record[k]).filter(Boolean);
        const fullAddr = parts.join(', ');
        return fullAddr ? (
          <div className="flex items-center space-x-1 max-w-[170px]" title={fullAddr}>
            <MapPin className="h-3 w-3 text-slate-400 shrink-0" />
            <span className="truncate text-[11px] text-slate-600">{fullAddr}</span>
          </div>
        ) : (
          <span className="text-slate-300">-</span>
        );
      }

      case 'CURRENCY': {
        const ccy = col.currencyKey ? record[col.currencyKey] : 'VND';
        const numVal = Number(val || 0);
        return (
          <span className="font-mono font-semibold text-slate-800">
            {numVal.toLocaleString('en-US', { minimumFractionDigits: 2, maximumFractionDigits: 2 })}{' '}
            <span className="text-[10px] text-slate-400 font-normal">{ccy}</span>
          </span>
        );
      }

      case 'STATUS_DOUBLE': {
        const subStatus = col.subKey ? record[col.subKey] : null;
        const isAct = val === 'ACTIVE';
        return (
          <div className="space-y-1">
            <div>
              <span
                className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${isAct ? 'bg-emerald-100 text-emerald-800' : 'bg-red-100 text-red-800'
                  }`}
              >
                {val || '-'}
              </span>
            </div>
            {subStatus && (
              <div>
                <span
                  className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${subStatus === 'VERIFIED'
                    ? 'bg-blue-100 text-blue-800'
                    : 'bg-amber-100 text-amber-800'
                    }`}
                >
                  {subStatus}
                </span>
              </div>
            )}
          </div>
        );
      }

      case 'STATUS_BADGE': {
        const isAct = val === 'ACTIVE';
        const isDormant = val === 'DORMANT';
        const isFrozen = val === 'FROZEN';
        let cls = 'bg-slate-100 text-slate-700';
        if (isAct) cls = 'bg-emerald-100 text-emerald-800';
        else if (isDormant) cls = 'bg-amber-100 text-amber-800';
        else if (isFrozen) cls = 'bg-red-100 text-red-800';

        return (
          <span className={`rounded px-2 py-0.5 text-[10px] font-bold ${cls}`}>
            {val || '-'}
          </span>
        );
      }

      default:
        return <span>{val !== undefined && val !== null ? String(val) : '-'}</span>;
    }
  };

  // Row action execution
  const handleRowAction = (action, record) => {
    if (!onNavigate) return;
    const params = {};
    if (action.paramsMapping) {
      Object.entries(action.paramsMapping).forEach(([targetKey, srcKey]) => {
        params[targetKey] = record[srcKey];
      });
    }
    onNavigate(action.targetFormCode, params);
  };

  if (!schema) return null;

  return (
    <div className="space-y-5 animate-fadeIn">
      {/* 1. Top Action & Header Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="rounded bg-blue-100 px-2 py-0.5 font-mono text-xs font-semibold text-blue-800">
              {schema.trxCode || schema.formCode}
            </span>
            <span className="text-xs font-mono text-slate-400">
              {schema.module || 'CBS'} • Inquiry Grid
            </span>
          </div>
          <h2 className="mt-1 text-lg font-bold text-slate-900">
            {schema.title || 'Data Directory & Search'}
          </h2>
          {schema.description && (
            <p className="text-xs text-slate-500 mt-0.5">{schema.description}</p>
          )}
        </div>

        {/* Top Action Buttons (hidden in modalMode) */}
        {!modalMode && schema.topActions && schema.topActions.length > 0 && (
          <div className="flex items-center space-x-2">
            {schema.topActions.map((action) => {
              const label = (action.label || '').replace(/^\+\s*/, '');
              return (
                <button
                  key={action.code}
                  type="button"
                  onClick={() => onNavigate && onNavigate(action.targetFormCode, {})}
                  className="rounded-xl bg-blue-600 px-4 py-2 text-xs font-bold text-white shadow-xs hover:bg-blue-700 transition"
                >
                  <span>{label}</span>
                </button>
              );
            })}
          </div>
        )}
      </div>

      {/* 2. Search Controls Card */}
      <div className="rounded-2xl border border-slate-200 bg-slate-50/70 p-4 space-y-3">
        {/* Quick Search Row */}
        <div className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
          <div className="relative flex-1">
            <div className="pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3">
              <Search className="h-4 w-4 text-slate-400" />
            </div>
            <input
              type="text"
              value={keyword}
              onChange={(e) => setKeyword(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder={
                schema.quickSearch?.placeholder || 'Search by keyword or identifier...'
              }
              className="w-full rounded-xl border border-slate-300 bg-white py-2 pl-9 pr-4 text-xs font-medium text-slate-800 placeholder-slate-400 transition focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <button
            type="button"
            onClick={() => executeSearch()}
            disabled={isLoading}
            className="flex items-center justify-center space-x-1.5 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white hover:bg-slate-800 transition disabled:opacity-50"
          >
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
            <span>Search</span>
          </button>

          {schema.advanceFilters && schema.advanceFilters.length > 0 && (
            <button
              type="button"
              onClick={() => setIsAdvanceOpen(!isAdvanceOpen)}
              className={`flex items-center justify-center space-x-1.5 rounded-xl border px-3.5 py-2 text-xs font-semibold transition ${isAdvanceOpen || activeFilterCount > 0
                ? 'border-blue-600 bg-blue-50 text-blue-700'
                : 'border-slate-300 bg-white text-slate-700 hover:bg-slate-50'
                }`}
            >
              <Filter className="h-3.5 w-3.5" />
              <span>Advance Filters</span>
              {activeFilterCount > 0 && (
                <span className="rounded-full bg-blue-600 px-1.5 py-0.2 text-[9px] font-bold text-white">
                  {activeFilterCount}
                </span>
              )}
              {isAdvanceOpen ? (
                <ChevronUp className="h-3.5 w-3.5 ml-1" />
              ) : (
                <ChevronDown className="h-3.5 w-3.5 ml-1" />
              )}
            </button>
          )}
        </div>

        {/* Collapsible Advance Search Panel */}
        {isAdvanceOpen && schema.advanceFilters && schema.advanceFilters.length > 0 && (
          <div className="pt-3 border-t border-slate-200 grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-4 animate-fadeIn text-xs">
            {schema.advanceFilters.map((field) => {
              const val = filters[field.name] || '';

              if (field.type === 'SELECT') {
                const options = optionsMap[field.name] || field.options || [];
                return (
                  <div key={field.name}>
                    <label className="block text-[11px] font-bold text-slate-600 mb-1">
                      {field.label}
                    </label>
                    <select
                      value={val}
                      onChange={(e) => handleFilterChange(field.name, e.target.value)}
                      className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
                    >
                      <option value="">{field.placeholder || `All ${field.label}`}</option>
                      {options.map((opt, idx) => {
                        const optVal = typeof opt === 'string' ? opt : opt.value;
                        const optLbl = typeof opt === 'string' ? opt : opt.label;
                        return (
                          <option key={`${optVal}-${idx}`} value={optVal}>
                            {optLbl}
                          </option>
                        );
                      })}
                    </select>
                  </div>
                );
              }

              return (
                <div key={field.name}>
                  <label className="block text-[11px] font-bold text-slate-600 mb-1">
                    {field.label}
                  </label>
                  <input
                    type={field.type === 'NUMBER' ? 'number' : 'text'}
                    value={val}
                    onChange={(e) => handleFilterChange(field.name, e.target.value)}
                    onKeyDown={handleKeyDown}
                    placeholder={field.placeholder || `Enter ${field.label}...`}
                    className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
                  />
                </div>
              );
            })}

            {/* Filter Actions */}
            <div className="sm:col-span-3 lg:col-span-4 flex items-center justify-end space-x-2 pt-2">
              <button
                type="button"
                onClick={handleClearFilters}
                className="flex items-center space-x-1 rounded-lg border border-slate-300 bg-white px-3 py-1.5 text-xs font-semibold text-slate-600 hover:bg-slate-100 transition"
              >
                <RotateCcw className="h-3 w-3" />
                <span>Clear</span>
              </button>
              <button
                type="button"
                onClick={() => executeSearch()}
                className="rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-bold text-white hover:bg-blue-700 transition"
              >
                Apply Filters
              </button>
            </div>
          </div>
        )}
      </div>

      {/* 3. Data Grid Results Table */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-bold text-slate-600 px-1">
          <span>
            Search Results ({records.length} records found)
          </span>
          {isLoading && (
            <div className="flex items-center space-x-1 text-blue-600">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              <span>Querying Core Banking database...</span>
            </div>
          )}
        </div>

        <div className="overflow-x-auto overflow-y-scroll max-h-[500px] rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <tr>
                {(schema.columns || []).map((col) => (
                  <th key={col.key} className="py-3 px-3">
                    {col.label}
                  </th>
                ))}
                {(modalMode || (schema.rowActions && schema.rowActions.length > 0)) && (
                  <th className="py-3 px-3 text-right">{modalMode ? 'Action' : 'Actions'}</th>
                )}
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {records.length > 0 ? (
                records.map((record, rIdx) => (
                  <tr key={record.id || record.accountId || record.customerId || record.productCode || rIdx} className="hover:bg-blue-50/40 transition">
                    {(schema.columns || []).map((col) => (
                      <td key={`${col.key}-${rIdx}`} className="py-3 px-3">
                        {renderCell(record, col)}
                      </td>
                    ))}

                    {/* Row Action / Modal Select */}
                    {modalMode ? (
                      <td className="py-3 px-3 text-right">
                        <button
                          type="button"
                          onClick={() => onSelectRecord && onSelectRecord(record)}
                          className="rounded-md bg-blue-600 px-3 py-1 text-[11px] font-bold text-white hover:bg-blue-700 shadow-xs transition"
                        >
                          Select
                        </button>
                      </td>
                    ) : (
                      schema.rowActions && schema.rowActions.length > 0 && (
                        <td className="py-3 px-3 text-right">
                          <div className="flex items-center justify-end space-x-1.5">
                            {schema.rowActions.map((action) => {
                              const label = (action.label || '').replace(/^\+\s*/, '');
                              let btnStyle = 'border-slate-200 bg-white text-slate-700 hover:border-blue-500 hover:bg-blue-50 hover:text-blue-800';
                              if (action.style === 'emerald') {
                                btnStyle = 'border-slate-200 bg-white text-slate-700 hover:border-emerald-500 hover:bg-emerald-50 hover:text-emerald-800';
                              } else if (action.style === 'indigo') {
                                btnStyle = 'border-slate-200 bg-white text-slate-700 hover:border-indigo-500 hover:bg-indigo-50 hover:text-indigo-800';
                              } else if (action.style === 'amber') {
                                btnStyle = 'border-slate-200 bg-white text-slate-700 hover:border-amber-500 hover:bg-amber-50 hover:text-amber-800';
                              } else if (action.style === 'blue') {
                                btnStyle = 'border-slate-200 bg-white text-slate-700 hover:border-blue-500 hover:bg-blue-50 hover:text-blue-800';
                              }

                              return (
                                <button
                                  key={action.code}
                                  type="button"
                                  onClick={() => handleRowAction(action, record)}
                                  className={`rounded-md border px-2.5 py-1 text-[10px] font-bold transition ${btnStyle}`}
                                  title={action.title || label}
                                >
                                  {label}
                                </button>
                              );
                            })}
                          </div>
                        </td>
                      )
                    )}
                  </tr>
                ))
              ) : (
                <tr>
                  <td
                    colSpan={(schema.columns?.length || 0) + (modalMode || schema.rowActions?.length ? 1 : 0)}
                    className="py-10 text-center text-slate-400"
                  >
                    <FileQuestion className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                    <p className="text-xs font-semibold">No records found</p>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      Try searching with different keywords or clearing active filters.
                    </p>
                  </td>
                </tr>
              )}
            </tbody>
          </table>
        </div>
      </div>
    </div>
  );
}

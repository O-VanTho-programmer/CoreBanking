import React, { useState, useEffect } from 'react';
import { customerApi } from '../../services/customerApi';
import {
  Search,
  UserPlus,
  Filter,
  ChevronDown,
  ChevronUp,
  RotateCcw,
  User,
  CreditCard,
  FileText,
  Loader2,
  CheckCircle2,
  Clock,
  AlertTriangle,
  Building2,
  Phone,
  Mail,
  MapPin,
} from 'lucide-react';

export default function CustomerSearchWidget({ onNavigate }) {
  // Search state
  const [keyword, setKeyword] = useState('');
  const [isAdvanceOpen, setIsAdvanceOpen] = useState(false);
  const [catalogTypes, setCatalogTypes] = useState([]);
  const [isLoading, setIsLoading] = useState(false);
  const [customers, setCustomers] = useState([]);
  const [hasSearched, setHasSearched] = useState(false);

  // Advance filters state
  const [filters, setFilters] = useState({
    customerType: '',
    fullName: '',
    phoneNumber: '',
    emailAddress: '',
    gender: '',
    status: '',
    kycStatus: '',
    province: '',
  });

  // Load customer catalog types on mount
  useEffect(() => {
    async function loadCatalog() {
      try {
        const types = await customerApi.getCatalogue();
        setCatalogTypes(types || []);
      } catch (err) {
        console.warn('Failed to load customer catalog:', err.message);
      }
    }
    loadCatalog();
    // Run initial search
    handleSearch();
  }, []);

  const handleFilterChange = (field, val) => {
    setFilters((prev) => ({ ...prev, [field]: val }));
  };

  const handleClearFilters = () => {
    setKeyword('');
    setFilters({
      customerType: '',
      fullName: '',
      phoneNumber: '',
      emailAddress: '',
      gender: '',
      status: '',
      kycStatus: '',
      province: '',
    });
    handleSearch({}, '');
  };

  const handleSearch = async (overrideFilters = null, overrideKeyword = null) => {
    try {
      setIsLoading(true);
      const activeFilters = overrideFilters !== null ? overrideFilters : filters;
      const activeKeyword = overrideKeyword !== null ? overrideKeyword : keyword;

      const params = {};
      if (activeKeyword.trim()) params.keyword = activeKeyword.trim();
      if (activeFilters.customerType) params.customerType = activeFilters.customerType;
      if (activeFilters.fullName.trim()) params.fullName = activeFilters.fullName.trim();
      if (activeFilters.phoneNumber.trim()) params.phoneNumber = activeFilters.phoneNumber.trim();
      if (activeFilters.emailAddress.trim()) params.emailAddress = activeFilters.emailAddress.trim();
      if (activeFilters.gender) params.gender = activeFilters.gender;
      if (activeFilters.status) params.status = activeFilters.status;
      if (activeFilters.kycStatus) params.kycStatus = activeFilters.kycStatus;
      if (activeFilters.province.trim()) params.province = activeFilters.province.trim();

      const results = await customerApi.searchCustomers(params);
      setCustomers(results || []);
      setHasSearched(true);
    } catch (err) {
      console.error('Customer search error:', err.message);
    } finally {
      setIsLoading(false);
    }
  };

  const handleKeyDown = (e) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      handleSearch();
    }
  };

  // Count active advance filters
  const activeFilterCount = Object.values(filters).filter((v) => v !== '').length;

  return (
    <div className="space-y-5 animate-fadeIn">
      {/* Top Action Bar */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4 border-b border-slate-200 pb-4">
        <div>
          <div className="flex items-center space-x-2">
            <span className="rounded bg-blue-100 px-2 py-0.5 font-mono text-xs font-semibold text-blue-800">
              CUST_PROFILE
            </span>
            <span className="text-xs font-mono text-slate-400">Search & CIF 360</span>
          </div>
          <h2 className="mt-1 text-lg font-bold text-slate-900">
            Customer Profile & CIF Directory
          </h2>
          <p className="text-xs text-slate-500">
            Search, filter, and manage customer profile records across all banking segments.
          </p>
        </div>

        {/* Primary Action Button: Add New Customer */}
        <div>
          <button
            type="button"
            onClick={() => onNavigate && onNavigate('FORM_CUST_CREATE', {})}
            className="flex items-center space-x-2 rounded-xl bg-blue-600 px-4 py-2.5 text-xs font-bold text-white shadow-xs hover:bg-blue-700 transition"
          >
            <UserPlus className="h-4 w-4" />
            <span>+ Add New Customer</span>
          </button>
        </div>
      </div>

      {/* Search Controls Card */}
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
              placeholder="Search by CIF Number, Full Name, National ID, Phone Number..."
              className="w-full rounded-xl border border-slate-300 bg-white py-2 pl-9 pr-4 text-xs font-medium text-slate-800 placeholder-slate-400 transition focus:border-blue-600 focus:outline-none focus:ring-2 focus:ring-blue-100"
            />
          </div>

          <button
            type="button"
            onClick={() => handleSearch()}
            disabled={isLoading}
            className="flex items-center justify-center space-x-1.5 rounded-xl bg-slate-900 px-4 py-2 text-xs font-bold text-white hover:bg-slate-800 transition disabled:opacity-50"
          >
            {isLoading ? <Loader2 className="h-4 w-4 animate-spin" /> : <Search className="h-4 w-4" />}
            <span>Search</span>
          </button>

          <button
            type="button"
            onClick={() => setIsAdvanceOpen(!isAdvanceOpen)}
            className={`flex items-center justify-center space-x-1.5 rounded-xl border px-3.5 py-2 text-xs font-semibold transition ${
              isAdvanceOpen || activeFilterCount > 0
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
            {isAdvanceOpen ? <ChevronUp className="h-3.5 w-3.5 ml-1" /> : <ChevronDown className="h-3.5 w-3.5 ml-1" />}
          </button>
        </div>

        {/* Collapsible Advance Search Panel */}
        {isAdvanceOpen && (
          <div className="pt-3 border-t border-slate-200 grid grid-cols-1 gap-3 sm:grid-cols-3 lg:grid-cols-4 animate-fadeIn text-xs">
            {/* Customer Type Select (from Catalog) */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Customer Type</label>
              <select
                value={filters.customerType}
                onChange={(e) => handleFilterChange('customerType', e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              >
                <option value="">All Customer Types</option>
                {catalogTypes.map((cat) => (
                  <option key={cat.typeCode} value={cat.typeCode}>
                    {cat.typeName} ({cat.typeCode})
                  </option>
                ))}
              </select>
            </div>

            {/* Full Name */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Full Legal Name</label>
              <input
                type="text"
                value={filters.fullName}
                onChange={(e) => handleFilterChange('fullName', e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. Nguyen Van..."
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              />
            </div>

            {/* Phone Number */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Phone Number</label>
              <input
                type="text"
                value={filters.phoneNumber}
                onChange={(e) => handleFilterChange('phoneNumber', e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. 0901234567..."
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              />
            </div>

            {/* Email Address */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Email Address</label>
              <input
                type="text"
                value={filters.emailAddress}
                onChange={(e) => handleFilterChange('emailAddress', e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. customer@bank.com..."
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              />
            </div>

            {/* Gender */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Gender</label>
              <select
                value={filters.gender}
                onChange={(e) => handleFilterChange('gender', e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              >
                <option value="">All Genders</option>
                <option value="MALE">Male</option>
                <option value="FEMALE">Female</option>
                <option value="OTHER">Other / Corporate</option>
              </select>
            </div>

            {/* Status */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Account Status</label>
              <select
                value={filters.status}
                onChange={(e) => handleFilterChange('status', e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              >
                <option value="">All Statuses</option>
                <option value="ACTIVE">Active</option>
                <option value="SUSPENDED">Suspended</option>
                <option value="DECEASED">Deceased</option>
              </select>
            </div>

            {/* KYC Status */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">KYC Status</label>
              <select
                value={filters.kycStatus}
                onChange={(e) => handleFilterChange('kycStatus', e.target.value)}
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              >
                <option value="">All KYC Statuses</option>
                <option value="VERIFIED">Verified</option>
                <option value="PENDING">Pending</option>
                <option value="REJECTED">Rejected</option>
              </select>
            </div>

            {/* Province / City */}
            <div>
              <label className="block text-[11px] font-bold text-slate-600 mb-1">Province / City</label>
              <input
                type="text"
                value={filters.province}
                onChange={(e) => handleFilterChange('province', e.target.value)}
                onKeyDown={handleKeyDown}
                placeholder="e.g. Ho Chi Minh..."
                className="w-full rounded-lg border border-slate-300 bg-white p-2 text-xs font-medium focus:border-blue-600 focus:outline-none"
              />
            </div>

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
                onClick={() => handleSearch()}
                className="rounded-lg bg-blue-600 px-4 py-1.5 text-xs font-bold text-white hover:bg-blue-700 transition"
              >
                Apply Filters
              </button>
            </div>
          </div>
        )}
      </div>

      {/* Customer Data Grid Results Table */}
      <div className="space-y-2">
        <div className="flex items-center justify-between text-xs font-bold text-slate-600 px-1">
          <span>Search Results ({customers.length} records found)</span>
          {isLoading && (
            <div className="flex items-center space-x-1 text-blue-600">
              <Loader2 className="h-3.5 w-3.5 animate-spin" />
              <span>Querying Oracle 19c database...</span>
            </div>
          )}
        </div>

        <div className="overflow-x-auto rounded-xl border border-slate-200 bg-white shadow-xs">
          <table className="w-full text-left text-xs border-collapse">
            <thead className="border-b border-slate-200 bg-slate-50 text-[11px] font-bold uppercase tracking-wider text-slate-500">
              <tr>
                <th className="py-3 px-3">CIF Number</th>
                <th className="py-3 px-3">Customer Name</th>
                <th className="py-3 px-3">Type & Segment</th>
                <th className="py-3 px-3">National ID / Tax</th>
                <th className="py-3 px-3">Contact</th>
                <th className="py-3 px-3">Address</th>
                <th className="py-3 px-3">Status</th>
                <th className="py-3 px-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-slate-100 text-slate-700">
              {customers.length > 0 ? (
                customers.map((c) => (
                  <tr key={c.customerId} className="hover:bg-blue-50/40 transition">
                    {/* CIF Number */}
                    <td className="py-3 px-3 font-mono font-bold text-blue-700">
                      {c.cifNumber}
                    </td>

                    {/* Customer Name & Gender */}
                    <td className="py-3 px-3">
                      <div className="font-semibold text-slate-900">{c.fullName}</div>
                      <div className="text-[10px] text-slate-400 capitalize">
                        {c.gender ? c.gender.toLowerCase() : 'other'}
                      </div>
                    </td>

                    {/* Type & Segment */}
                    <td className="py-3 px-3">
                      <span className="rounded bg-slate-100 px-2 py-0.5 text-[10px] font-bold text-slate-700">
                        {c.customerType}
                      </span>
                    </td>

                    {/* National ID / Tax */}
                    <td className="py-3 px-3 font-mono text-[11px]">
                      <div>{c.nationalId}</div>
                      {c.taxNumber && (
                        <div className="text-[10px] text-slate-400">Tax: {c.taxNumber}</div>
                      )}
                    </td>

                    {/* Contact */}
                    <td className="py-3 px-3 text-[11px] space-y-0.5">
                      {c.phoneNumber ? (
                        <div className="flex items-center space-x-1 text-slate-700">
                          <Phone className="h-3 w-3 text-slate-400 shrink-0" />
                          <span>{c.phoneNumber}</span>
                        </div>
                      ) : (
                        <span className="text-slate-300">-</span>
                      )}
                      {c.emailAddress && (
                        <div className="flex items-center space-x-1 text-slate-500 text-[10px]">
                          <Mail className="h-2.5 w-2.5 text-slate-400 shrink-0" />
                          <span className="truncate max-w-[120px]">{c.emailAddress}</span>
                        </div>
                      )}
                    </td>

                    {/* Address */}
                    <td className="py-3 px-3 text-[11px] text-slate-600 max-w-[160px] truncate">
                      {c.province || c.district || c.streetAddress ? (
                        <div className="flex items-center space-x-1" title={`${c.streetAddress || ''}, ${c.district || ''}, ${c.province || ''}`}>
                          <MapPin className="h-3 w-3 text-slate-400 shrink-0" />
                          <span className="truncate">
                            {[c.district, c.province].filter(Boolean).join(', ') || c.streetAddress}
                          </span>
                        </div>
                      ) : (
                        <span className="text-slate-300">-</span>
                      )}
                    </td>

                    {/* Status & KYC */}
                    <td className="py-3 px-3 space-y-1">
                      <div>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${
                            c.status === 'ACTIVE'
                              ? 'bg-emerald-100 text-emerald-800'
                              : 'bg-red-100 text-red-800'
                          }`}
                        >
                          {c.status}
                        </span>
                      </div>
                      <div>
                        <span
                          className={`rounded px-1.5 py-0.5 text-[9px] font-bold ${
                            c.kycStatus === 'VERIFIED'
                              ? 'bg-blue-100 text-blue-800'
                              : 'bg-amber-100 text-amber-800'
                          }`}
                        >
                          {c.kycStatus}
                        </span>
                      </div>
                    </td>

                    {/* Action Buttons */}
                    <td className="py-3 px-3 text-right">
                      <div className="flex items-center justify-end space-x-1.5">
                        <button
                          type="button"
                          onClick={() =>
                            onNavigate &&
                            onNavigate('FORM_ACCOUNT_OPEN', {
                              customerId: c.customerId,
                              cifNumber: c.cifNumber,
                              customerName: c.fullName,
                            })
                          }
                          className="rounded-md border border-slate-200 bg-white px-2 py-1 text-[10px] font-bold text-slate-700 hover:border-emerald-500 hover:bg-emerald-50 hover:text-emerald-800 transition"
                          title="Open CASA Deposit Account for this customer"
                        >
                          + CASA
                        </button>

                        <button
                          type="button"
                          onClick={() =>
                            onNavigate &&
                            onNavigate('FORM_CREDIT_ACCOUNT_OPEN', {
                              customerId: c.customerId,
                              cifNumber: c.cifNumber,
                              customerName: c.fullName,
                            })
                          }
                          className="rounded-md border border-slate-200 bg-white px-2 py-1 text-[10px] font-bold text-slate-700 hover:border-indigo-500 hover:bg-indigo-50 hover:text-indigo-800 transition"
                          title="Open Credit Loan Account for this customer"
                        >
                          + Loan
                        </button>
                      </div>
                    </td>
                  </tr>
                ))
              ) : (
                <tr>
                  <td colSpan="8" className="py-10 text-center text-slate-400">
                    <User className="h-8 w-8 mx-auto mb-2 text-slate-300" />
                    <p className="text-xs font-semibold">No customer records found</p>
                    <p className="text-[11px] text-slate-400 mt-0.5">
                      Try searching with different keywords or clearing your filters.
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

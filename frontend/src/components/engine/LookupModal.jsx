import React, { useState, useEffect } from 'react';
import { formApi } from '../../services/formApi';
import GenericSearchWidget from './GenericSearchWidget';
import { X, Loader2, AlertCircle } from 'lucide-react';

export default function LookupModal({
  isOpen,
  onClose,
  modalFormCode,
  onSelectRecord,
  title = null,
}) {
  const [schema, setSchema] = useState(null);
  const [isLoading, setIsLoading] = useState(false);
  const [errorMessage, setErrorMessage] = useState(null);

  useEffect(() => {
    if (!isOpen || !modalFormCode) {
      setSchema(null);
      return;
    }

    let isMounted = true;
    async function fetchModalSchema() {
      try {
        setIsLoading(true);
        setErrorMessage(null);
        const data = await formApi.getFormByCode(modalFormCode);
        if (isMounted) setSchema(data);
      } catch (err) {
        if (isMounted) {
          setErrorMessage(`Failed to load lookup form [${modalFormCode}]: ${err.message}`);
        }
      } finally {
        if (isMounted) setIsLoading(false);
      }
    }

    fetchModalSchema();
    return () => {
      isMounted = false;
    };
  }, [isOpen, modalFormCode]);

  if (!isOpen) return null;

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center p-4 bg-slate-900/50 backdrop-blur-xs animate-fadeIn">
      <div className="relative w-full max-w-5xl max-h-[90vh] overflow-y-auto rounded-2xl bg-white p-6 shadow-2xl border border-slate-200">
        {/* Modal Header */}
        <div className="flex items-center justify-between border-b border-slate-200 pb-3 mb-4">
          <div>
            <span className="text-[10px] font-mono font-bold uppercase tracking-wider text-blue-600">
              Lookup Directory • {modalFormCode}
            </span>
            <h3 className="text-base font-bold text-slate-900">
              {title || schema?.title || 'Catalogue Lookup'}
            </h3>
          </div>
          <button
            type="button"
            onClick={onClose}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-100 hover:text-slate-600 transition"
          >
            <X className="h-5 w-5" />
          </button>
        </div>

        {/* Modal Body */}
        {isLoading && (
          <div className="flex h-64 flex-col items-center justify-center space-y-3">
            <Loader2 className="h-8 w-8 animate-spin text-blue-600" />
            <p className="text-xs font-medium text-slate-500">
              Loading {modalFormCode} from Core Banking Catalog...
            </p>
          </div>
        )}

        {errorMessage && (
          <div className="flex items-start space-x-3 rounded-xl border border-red-200 bg-red-50 p-4 text-xs text-red-800">
            <AlertCircle className="h-4 w-4 text-red-500 shrink-0 mt-0.5" />
            <span>{errorMessage}</span>
          </div>
        )}

        {!isLoading && !errorMessage && schema && (
          <GenericSearchWidget
            schema={schema}
            modalMode={true}
            onSelectRecord={(record) => {
              if (onSelectRecord) onSelectRecord(record);
              onClose();
            }}
          />
        )}
      </div>
    </div>
  );
}

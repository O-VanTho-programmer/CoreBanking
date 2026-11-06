import React from 'react';
import { X, FileText, Plus, MoreHorizontal } from 'lucide-react';

export default function WorkspaceTabs({
  tabs = [],
  activeTabId,
  onSelectTab,
  onCloseTab,
  onCloseOtherTabs,
  onCloseAllTabs,
  onNewTab,
}) {
  return (
    <div className="flex items-center justify-between border-b border-slate-200 bg-slate-100/80 px-2 pt-2 select-none overflow-x-auto">
      {/* Scrollable Tab Strip */}
      <div className="flex items-center space-x-1 overflow-x-auto scrollbar-none py-0.5">
        {tabs.map((tab) => {
          const isActive = tab.id === activeTabId;

          return (
            <div
              key={tab.id}
              onClick={() => onSelectTab(tab.id)}
              className={`group relative flex items-center space-x-2 rounded-t-xl px-3.5 py-2 text-xs font-bold transition cursor-pointer border-t border-x ${
                isActive
                  ? 'bg-white text-blue-900 border-slate-200 shadow-xs'
                  : 'bg-slate-200/70 text-slate-600 border-transparent hover:bg-slate-200 hover:text-slate-900'
              }`}
            >
              <FileText className={`h-3.5 w-3.5 shrink-0 ${isActive ? 'text-blue-700' : 'text-slate-400'}`} />
              
              <span className="truncate max-w-[140px] tracking-tight">
                {tab.title}
              </span>

              {/* Dirty indicator */}
              {tab.isDirty && (
                <span className="h-1.5 w-1.5 rounded-full bg-amber-500 shrink-0" title="Unsaved changes" />
              )}

              {/* Close Button */}
              {tabs.length > 1 && (
                <button
                  type="button"
                  onClick={(e) => {
                    e.stopPropagation();
                    onCloseTab(tab.id);
                  }}
                  className="rounded-md p-0.5 text-slate-400 hover:bg-slate-200 hover:text-slate-800 transition"
                  title="Close tab"
                >
                  <X className="h-3 w-3" />
                </button>
              )}
            </div>
          );
        })}

        {/* New Tab Quick Button */}
        {tabs.length < 8 && onNewTab && (
          <button
            type="button"
            onClick={onNewTab}
            className="rounded-lg p-1.5 text-slate-400 hover:bg-slate-200 hover:text-slate-700 transition"
            title="Open new transaction tab"
          >
            <Plus className="h-4 w-4" />
          </button>
        )}
      </div>

      {/* Tabs Actions Menu (Close Others, Close All) */}
      {tabs.length > 1 && (
        <div className="flex items-center space-x-2 pb-1 text-[11px] text-slate-500 font-medium">
          <button
            type="button"
            onClick={onCloseOtherTabs}
            className="hover:text-slate-900 transition underline underline-offset-2"
          >
            Close Others
          </button>
          <span>•</span>
          <button
            type="button"
            onClick={onCloseAllTabs}
            className="hover:text-red-600 transition underline underline-offset-2"
          >
            Close All
          </button>
        </div>
      )}
    </div>
  );
}

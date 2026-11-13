# 🖥️ Core Banking Teller Portal (Frontend)

Modern, high-efficiency Single Page Application (SPA) designed for bank branch tellers, supervisors, and operations officers. Built with **React 18**, **Vite**, and **Tailwind CSS**, featuring a **Server-Driven UI (SDUI)** engine.

---

## 🚀 Key Features

### 1. Server-Driven UI (SDUI) Form Engine
- **Dynamic Form Rendering**: Forms are dynamically generated at runtime from schema definitions loaded via `/api/v1/forms/{formCode}`.
- **Dynamic Field Types**: Full support for `TEXT`, `TEXTAREA`, `CURRENCY` (VND formatting), `DATE`, `CODELIST_SELECT` (backed by Redis CodeLists), and server-driven `ACCOUNT_LOOKUP` / `CUSTOMER_LOOKUP`.
- **Form Actions & Workflow Steps**: Dynamic multi-step form workflows (e.g. `LOOKUP_SOURCE_ACCOUNT`, `CALCULATE_TRANSFER_FEE`) that evaluate rules and update form state without page reloads.
- **In-Form Accounting Leg Breakdown**: Displays debit/credit journal entries directly below form action buttons upon successful submission for immediate teller audit.

### 2. Multi-Tab Workspace & Spotlight Command Palette
- **Multi-Tab Interface**: Work on multiple customer transactions simultaneously without losing form state (`WorkspaceTabs`, `TabFormContainer`).
- **Spotlight Search (`Ctrl + K`)**: Quick-launcher dialog querying `/api/v1/forms/shortcuts` to instantly find and open any transaction screen by keyword, hotkey, or category.

### 3. EOD Accounting & Operations Cockpit
- **Working Date Controls**: View and inspect current operational banking date and cutoff status.
- **Cutoff Toggle**: Freeze online/external transaction ingress in preparation for batch processing.
- **8-Step EOD Batch Pipeline Execution**: Visual animated stepper tracking End-of-Day batch execution (Snapshots, Accruals, Repayments, Cold Archival, Rollover).
- **Trial Balance Reconciliation**: Real-time comparison of Total Macro Debits vs Credits and GL 4211 vs Sub-ledger CASA balances.

### 4. Polymorphic Banking Transaction Voucher / Receipt
- Clean, thermal-voucher black-and-white design.
- Specialized printable receipt layouts for:
  - **Cash Deposit Voucher**
  - **Cash Withdrawal Voucher**
  - **Internal Fund Transfer Voucher**
  - **Loan Origination / Disbursement Voucher**
  - **CASA Account Opening Certificate**

---

## 🛠️ Tech Stack

- **Framework**: React 18.3
- **Build Tool**: Vite 5.4
- **Styling**: Tailwind CSS 3.4
- **Icons**: Lucide React
- **HTTP Client**: Axios with baseURL fallback and response interceptors

---

## 📂 Project Structure

```
frontend/src/
├── App.jsx                     # Root application container & navigation
├── main.jsx                    # React entrypoint
├── index.css                   # Tailwind CSS root directives & custom font/animations
├── components/
│   ├── common/
│   │   ├── Header.jsx          # Top application bar (Working date, Cutoff badge, shortcuts)
│   │   └── ReceiptModal.jsx    # Polymorphic banking receipt / voucher modal
│   ├── engine/
│   │   ├── DynamicForm.jsx     # Core SDUI dynamic form renderer & workflow runner
│   │   ├── FormFieldRenderer.jsx # Specialized input widget dispatcher
│   │   ├── LookupModal.jsx     # Reusable search modal for CIF, Accounts, Catalogs
│   │   ├── GenericSearchWidget.jsx # Debounced autocomplete search input
│   │   └── widgets/            # Custom inputs (Currency, Date, CodeList, AccountLookup)
│   └── workspace/
│       ├── WorkspaceTabs.jsx   # Multi-tab bar with close & active indicators
│       └── TabFormContainer.jsx# Tab state container
├── pages/
│   ├── EodAccountingCockpit.jsx# EOD batch execution & Trial Balance dashboard
│   └── LendingConsole.jsx      # Credit product catalog & loan portfolio view
└── services/
    ├── apiClient.js            # Axios instance configured for /api/v1
    ├── formApi.js              # Form schemas, shortcuts, and workflow execution
    ├── transactionApi.js       # Financial mutations (Transfer, Deposit, Withdraw, Reverse)
    ├── accountApi.js           # Account balance inquiry and lookup
    ├── customerApi.js          # CIF customer search
    ├── lendingApi.js           # Loan origination and schedule inquiry
    └── eodApi.js               # EOD pipeline, operational cutoff, and trial balance
```

---

## 💻 Development & Build

### Install Dependencies
```bash
cd frontend
npm install
```

### Run Dev Server (Vite Hot-Reload)
```bash
npm run dev
```
The dev server starts on `http://localhost:5173` and proxies API requests to Spring Boot on `http://localhost:8080`.

### Production Build
```bash
npm run build
```
Builds optimized production assets to `frontend/dist/`.  
These static assets are packaged into Spring Boot's `src/main/resources/static/` for standalone single-jar distribution.

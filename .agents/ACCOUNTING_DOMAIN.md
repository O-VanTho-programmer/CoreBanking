# Financial Domain & Accounting Rules Guide

## 1. Double-Entry Accounting Matrix
Every banking transaction is an atomic event composed of balanced debits and credits:
- **Debit ($D$)**: Increases Assets and Expenses; Decreases Liabilities, Equity, and Revenue.
- **Credit ($C$)**: Increases Liabilities, Equity, and Revenue; Decreases Assets and Expenses.

### The Canonical Balance Equation
$$\text{Assets} = \text{Liabilities} + \text{Equity} + (\text{Revenue} - \text{Expenses})$$

---

## 2. Standard Posting Templates

### A. Cash Deposit (`CASH_DEP`)
Customer deposits 1,000,000 VND cash at branch counter:
1. `DEBIT`  GL `1011` (`SYS_CASH_VAULT` - Asset increases) : `1,000,000 VND`
2. `CREDIT` Customer CASA Account (`4211` - Liability increases) : `1,000,000 VND`

### B. Cash Withdrawal (`CASH_WDR`)
Customer withdraws 500,000 VND cash:
1. `DEBIT`  Customer CASA Account (`4211` - Liability decreases) : `500,000 VND`
2. `CREDIT` GL `1011` (`SYS_CASH_VAULT` - Asset decreases) : `500,000 VND`

### C. Internal Transfer with Fee (`TRF_INTERNAL`)
Alice transfers 2,000,000 VND to Bob with 11,000 VND fee (10,000 fee + 1,000 VAT):
- **Group 1 (Principal Movement)**:
  1. `DEBIT`  Alice CASA Account : `2,000,000 VND`
  2. `CREDIT` Bob CASA Account : `2,000,000 VND`
- **Group 2 (Fee & Tax Movement)**:
  3. `DEBIT`  Alice CASA Account : `11,000 VND`
  4. `CREDIT` GL `7111` (`SYS_FEE_INCOME` - Revenue increases) : `10,000 VND`
  5. `CREDIT` GL `3331` (`SYS_VAT_PAYABLE` - Liability increases) : `1,000 VND`

---

## 3. Reversals & Storno Logic
- When a posted transaction $T_{orig}$ is reversed, do **NOT** modify or delete original rows.
- Create a new transaction $T_{rev}$ with:
  - `status = 'COMPLETED'`
  - `original_trx_id = T_{orig}.trx_id`
  - `reversal_reason = <reason>`
- Post symmetric opposite entries:
  - For each line in $T_{orig}$ with `dr_cr = 'D'`, post a line in $T_{rev}$ with `dr_cr = 'C'`.
  - For each line in $T_{orig}$ with `dr_cr = 'C'`, post a line in $T_{rev}$ with `dr_cr = 'D'`.
- Mark $T_{orig}.is\_reversed = 'Y'$.

---

## 4. End-of-Day (EOD) Processing Pipeline
1. **Cut-off**: Set `SYSTEM_OPERATIONAL_STATUS.is_system_cutoff = 'Y'`. Reject incoming front-office transactions.
2. **Daily Interest Accrual**:
   - For all active savings/CASA accounts:
     $$\text{Accrued Interest} = \text{round\_down}\left(\text{Closing Balance} \times \frac{\text{Annual Rate}}{365}, \, 4\right)$$
   - Accumulate into `account.accrued_interest` and snapshot into `ACCOUNT_BALANCE_BUCKET`.
3. **Data Archival (`MoveDataHis`)**:
   - Move completed transactions from `TRX_JOURNAL_ACTIVE` to `TRX_JOURNAL_DONE`.
   - Move completed entries from `JOURNAL_ENTRY_ACTIVE` to `JOURNAL_ENTRY_DONE`.
4. **Trial Balance Verification**:
   - Assert $\sum \text{Debit} == \sum \text{Credit}$ across the bank ledger.
5. **Roll Working Date**:
   - Query `BANK_CALENDAR` for next business day.
   - Update `SYSTEM_OPERATIONAL_STATUS.current_working_date = next_date`.
   - Reopen system: `is_system_cutoff = 'N'`.

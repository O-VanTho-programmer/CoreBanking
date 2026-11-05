# 🏛️ Core Banking Rule: Bank Account Numbering Standard

## 1. Rule Definition: Pure Numeric 16-Digit Standard (Chuẩn Số Tài Khoản Thuần Số 16 Chữ Số)

In commercial Core Banking Systems (CBS), account numbers must strictly remain **pure numeric 16-digit strings (`CHAR(16)` / `VARCHAR2(16)`)**. 

**Mandatory Invariant**:
> **DO NOT** prepend alphabetic prefixes (such as `"LN"`, `"CASA"`, `"FD"`) when the account type is already intrinsically distinguished by its General Ledger (GL) Code.

---

## 2. Standard Structural Formula

$$\mathbf{AccountNumber} = \mathbf{BranchCode} \ (3\text{ digits}) + \mathbf{GL\ Code} \ (4\text{ digits}) + \mathbf{Unique\ Sequence} \ (9\text{ digits})$$

| Segment | Length | Purpose | Examples |
| :--- | :---: | :--- | :--- |
| **Branch Code** | **3 digits** | Identifies issuing branch / accounting unit. | `001` (Head Office / Sở Giao Dịch), `002` (Chi nhánh HCM) |
| **GL Code** | **4 digits** | Identifies statutory balance sheet account classification. | `4211` (CASA / Demand Deposit), `2111` (In-term Short-term Loan), `2112` (Overdue Loan) |
| **Sequence** | **9 digits** | Unique per-account running sequence padded with zeros. | `000000101`, `000001001` |

### Symmetrical Concrete Examples:
* **Deposit Account (CASA / DPT)**:
  - Branch: `001`, GL: `4211`, Seq: `000001001` $\longrightarrow$ **`0014211000001001`**
* **Credit Account (Loan / CRD)**:
  - Branch: `001`, GL: `2111`, Seq: `000001001` $\longrightarrow$ **`0012111000001001`**

---

## 3. Rationale & Core Banking Benefits
1. **System Uniformity & Clean APIs**: Mobile Banking, Counter Teller, ATM, Interbank Switch (NAPAS / SWIFT), and Automated Clearing House (ACH) require pure numeric account numbers.
2. **Zero Ambiguity**: Auditors and Core Banking Engines distinguish Deposit vs. Lending directly via GL Code (`4211` = Liability, `2111` = Asset).
3. **Storage & Index Optimization**: Pure numeric strings allow direct integer parsing, faster B-Tree indexing, and efficient partitioning in Oracle 19c.

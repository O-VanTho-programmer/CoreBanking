# Database Standards & Schema Design Guidelines

## 1. Oracle Naming & Data Type Standards
- **Table & Column Names**: `UPPER_SNAKE_CASE` (e.g., `ACCOUNT_BALANCE_BUCKET`, `working_date`).
- **Primary Keys**: Always `NUMBER(19)` using global sequence `CBS_GLOBAL_SEQ.NEXTVAL`.
- **Monetary Amounts**: `NUMBER(18, 4)` NOT NULL. Represents 4 decimal places for sub-cent interest precision.
- **Currencies**: `CHAR(3)` NOT NULL. ISO 4217 code (e.g., `VND`, `USD`, `EUR`). **NO implicit default value allowed**.
- **Booleans / Flags**: `CHAR(1)` with check constraints `CHECK (col IN ('Y', 'N'))` or `('D', 'C')`.
- **Timestamps**: `TIMESTAMP WITH TIME ZONE DEFAULT SYSTIMESTAMP NOT NULL`.
- **Business Dates**: `DATE NOT NULL` (represents value date or working date without clock drift).

---

## 2. Table Classifications & Lifecycles

### A. Hot vs. Cold Storage (Neptune CBS Pattern)
1. **Hot Active Tables (`TRX_JOURNAL_ACTIVE`, `JOURNAL_ENTRY_ACTIVE`)**:
   - Contains only today's in-flight and completed transactions.
   - Kept under 50,000 rows. Indices stay pinned in Oracle SGA Buffer Cache.
2. **Cold Historical Tables (`TRX_JOURNAL_DONE`, `JOURNAL_ENTRY_DONE`)**:
   - Contains immutable transaction history partitioned by month.
   - End-of-Day (EOD) batch task moves completed records from Hot to Cold.

### B. Three-Tier Account Abstraction
1. `ACCOUNT_MAPPING`: Logical abstract concept (e.g., `SYS_CASH_VAULT`, `SYS_FEE_INCOME`).
2. `ACCOUNT_MAPPING_DETAIL`: Resolves `(mapping_id, branch_code, currency, product_code)` $\rightarrow$ physical `physical_gl_code`.
3. `GL_ACCOUNT`: Physical Chart of Accounts node on the Balance Sheet.

### C. Governed Posting Rule Matrix
- `ACCOUNTING_RULE_DEFINITION` defines legs per `trx_code`.
- Governed by `effective_from` and `effective_to`.
- Amount sources restricted to controlled catalog (`TRX_AMOUNT`, `FEE_AMOUNT`, `TAX_AMOUNT`, `PENALTY_AMOUNT`).

---

## 3. Indexing & Foreign Key Rules in Oracle
- In Oracle, **ALWAYS index foreign key columns**. Unindexed foreign keys cause table-level share locks on parent tables during updates/deletes.
- Primary key constraints must explicitly be named: `CONSTRAINT pk_<table> PRIMARY KEY (<cols>)`.
- Unique constraints must be named: `CONSTRAINT uq_<table>_<name> UNIQUE (<cols>)`.

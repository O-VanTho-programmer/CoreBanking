# Core Banking System - Project Charter & Architecture Rules

## 1. Project Mission & Tech Stack
- **Target**: Mini Core Banking Engine (Enterprise Quality, Portfolio Showcase for Senior Core Banking Backend Role).
- **Core Stacks**:
  - **Language**: Java 21 (Temurin 21 LTS - Virtual Threads, Pattern Matching, Records).
  - **Framework**: Spring Boot 3.3.4 (Modular Monolith with Spring Modulith).
  - **Database**: Oracle Database (Local Service `ORCL` on port 1521).
  - **Migration**: Flyway DB Migration.
  - **Data Access**: Spring Data JPA + JDBC Client (for high-performance batch and deterministic locking).
  - **Precision**: Strict `BigDecimal` with 4 decimal places (`NUMBER(18, 4)` in DB), zero floating-point arithmetic.

---

## 2. Inviolable Core Banking Architectural Invariants

### Invariant 1: The Double-Entry Equilibrium
For every transaction $T$, the transaction cannot be committed unless:
$$\sum_{i \in T, \, \text{dr\_cr} = 'D'} \text{amount}_i = \sum_{j \in T, \, \text{dr\_cr} = 'C'} \text{amount}_j$$
- Any unbalanced transaction must immediately throw `UnbalancedTransactionException` and roll back.
- Ledger lines (`JOURNAL_ENTRY`) are **append-only and immutable**. Never execute `UPDATE` or `DELETE` on financial entries.

### Invariant 2: Balance Integrity Equation
At all times across all threads:
$$\text{Available Balance} = \text{Ledger Balance} - \text{Hold Balance}$$
- Withdrawals/Transfers check: $\text{Available Balance} \ge \text{Debit Amount}$.
- Balance mutations update `ledger_balance` while leaving active `hold_balance` intact.

### Invariant 3: Deterministic Row Locking (Deadlock Prevention)
When executing transfers involving multiple accounts (e.g., Account A $\rightarrow$ Account B):
- **ALWAYS** sort account IDs in ascending order:
  ```sql
  SELECT * FROM ACCOUNT WHERE account_id IN (:id1, :id2) ORDER BY account_id ASC FOR UPDATE;
  ```
- Never acquire row locks in arbitrary or request-order direction.

### Invariant 4: Operational Working Date Decoupling
- **NEVER** use `LocalDate.now()` or `Instant.now()` for business/accounting value dates.
- All financial transactions and daily interest calculations must read from `SYSTEM_OPERATIONAL_STATUS.current_working_date` (derived from `BANK_CALENDAR`).

### Invariant 5: Two-Tier Composite Idempotency
- Every incoming transaction request must carry `(channel_id, idempotency_key)`.
- Backend must verify uniqueness against `TRX_JOURNAL_ACTIVE` (Hot) and `TRX_JOURNAL_DONE` (Cold).

### Invariant 6: Status Validation via Dynamic CodeList
- Do not hardcode status checks with `CHECK CONSTRAINT` in DB.
- Use the dynamic `CODELIST` & `CODELIST_VALUE` tables.
- Enforce validation at the Java Domain/DTO level using `@ValidCodeList(group = "...")` backed by Redis cache.

# 🏦 Posting-Driven Transaction Architecture in Core Banking

## 1. Overview & Comparison

In modern enterprise Core Banking systems (e.g., Neptune CBS, Temenos T24, Finacle), transaction processing is strictly **Posting-Driven** rather than balance-driven.

Instead of hardcoding account balance mutations inside service classes and treating accounting as an afterthought, every financial operation is dynamically governed by a pre-configured **Posting Rule Matrix** (`ACCOUNTING_RULE_DEFINITION`).

---

## 2. Architecture Comparison

### Anti-Pattern: Balance-First with Post-Validation (Brittle)

```mermaid
flowchart TD
    A["1. Incoming FO Request\n(e.g., Cash Deposit 1M VND)"] --> B["2. Hardcode Balance Mutation\n(UPDATE ACCOUNT SET ledger_balance = ledger_balance + 1M)"]
    B --> C["3. Create Accounting Journal Lines\n(Hardcoded / Post-facto entry)"]
    C --> D{"4. Equilibrium Verification\nSUM(Debit) == SUM(Credit)?"}
    D -- "Failed" --> E["Database Rollback\n(Dirty updates had to be discarded)"]
    D -- "Success" --> F["Transaction Commit"]
```

*Pitfalls*:
- Business logic is fragmented across service classes (`deposit()`, `withdraw()`, `transfer()`) and accounting tables.
- Adding new banking products (e.g., Loan Disbursement, Collateral Fee, EOD Capitalization) requires modifying Java code.
- Higher database contention due to early row mutations before transaction validation.

---

### Enterprise Pattern: Posting-Driven Transaction Lifecycle (Adopted)

#### High-Level Workflow (Flowchart)

```mermaid
flowchart TD
    A["1. Incoming Request\n(e.g., Cash Deposit 1M VND)"] --> B["2. Evaluate Posting Rule Matrix\n(In-Memory on RAM)"]
    B --> C["3. Resolve Abstract Roles to Physical GL\n(SYS_CASH_VAULT -> 1011, CASA -> 4211)"]
    C --> D{"4. Pre-Validation Equilibrium in RAM\nSUM(Debit) == SUM(Credit)?"}
    D -- "Unbalanced" --> E["Fast-Fail: Throw Exception\n(Zero DB mutations, Clean Reject)"]
    D -- "Balanced" --> F["5. Apply Generic Balance Mutation\n(Driven by verified JournalLegs)"]
    F --> G["6. Append Immutable Ledger Entries\n(TRX_JOURNAL + JOURNAL_ENTRY)"]
    G --> H["7. Atomic Database Commit"]
```

#### Detailed Component Interaction (Sequence Diagram)

```mermaid
sequenceDiagram
    autonumber
    actor Channel as Front Office / Channel (Counter/Mobile)
    participant TxService as Transaction Orchestrator
    participant RuleEngine as Posting Engine
    participant Resolver as Account Resolver Service
    participant Invariant as Balance & Equilibrium Guard
    participant DB as Oracle Database (Hot Ledger)

    Channel->>TxService: Submit Transaction (e.g. CASH_DEP, 1,000,000 VND)
    TxService->>TxService: Idempotency Check (channel_id, idempotency_key)
    TxService->>DB: Lock Customer Account(s) (ORDER BY account_id ASC FOR UPDATE)
    
    rect rgb(240, 248, 255)
        Note over TxService, RuleEngine: In-Memory Posting Generation & Validation
        TxService->>RuleEngine: evaluate(PostingRequest)
        RuleEngine->>DB: Load ACCOUNTING_RULE_DEFINITION (or Redis cache)
        RuleEngine->>Resolver: Resolve Abstract Roles (e.g. SYS_CASH_VAULT -> GL 1011)
        Resolver-->>RuleEngine: Return physical GL accounts
        RuleEngine->>RuleEngine: Build candidate JournalLeg items
        RuleEngine->>Invariant: Validate Double-Entry Equilibrium: SUM(Debit) == SUM(Credit)
        
        alt Imbalanced Rules Detected
            Invariant-->>TxService: Throw UnbalancedTransactionException
            TxService-->>Channel: Return Error (Zero DB mutations, Clean Fail-Fast)
        else Equilibrium Satisfied
            Invariant-->>TxService: Return verified List<JournalLeg>
        end
    end

    rect rgb(245, 255, 250)
        Note over TxService, DB: Atomic Balance Mutation & Ledger Commit
        TxService->>Invariant: Check Available Balance for Debit legs: (Ledger - Hold >= Debit)
        TxService->>DB: Mutate customer ledger_balance based on verified legs
        TxService->>DB: INSERT INTO TRX_JOURNAL_ACTIVE (status = 'PROCESSING')
        TxService->>DB: INSERT INTO JOURNAL_ENTRY_ACTIVE (immutable legs)
        TxService->>DB: UPDATE TRX_JOURNAL_ACTIVE (status = 'COMPLETED')
        TxService->>DB: COMMIT (Single Atomic Unit)
    end

    TxService-->>Channel: Transaction Successful (TRX ID, Value Date, Balances)
```

---

## 3. Key Benefits

1. **Single Source of Truth**:
   The `ACCOUNTING_RULE_DEFINITION` table completely governs which accounts are debited or credited. Java code does not contain hardcoded financial logic.
2. **Fail-Fast in RAM**:
   If a rule matrix definition is asymmetric or corrupted, `UnbalancedTransactionException` is raised immediately in memory before any database write occurs.
3. **Internal GL Scalability (No Hotspot Locks)**:
   Bank internal GL accounts (such as Cash Vault `1011`, Fee Income `7111`, Interest Expense `8011`) are not row-locked for balance updates on every concurrent customer transaction. They are written as append-only `JOURNAL_ENTRY` rows, and aggregated during End-of-Day (EOD) processing, eliminating database bottlenecks.
4. **Auditability & Regulatory Compliance**:
   Every balance mutation on a customer account is strictly backed by an immutable `JOURNAL_ENTRY` row carrying matching value dates, currency, and transaction lineage.

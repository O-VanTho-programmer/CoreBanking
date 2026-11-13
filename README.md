# 🏦 Core Banking System (Mini Core Engine & Teller Portal)

> Enterprise Mini Core Banking Engine & Modern Teller Portal built with **Java 21**, **Spring Boot 3.3.4**, **Oracle Database 19c**, **Redis**, and **React 18**.  
> Architecture: **Modular Monolith** with strict domain boundaries, double-entry bookkeeping guarantees, and Server-Driven UI (SDUI).

---

## 📌 Core Banking Invariants & Architecture Highlights

1. **Inviolable Double-Entry Bookkeeping**:
   $$\sum \text{Debit} == \sum \text{Credit}$$
   Every accounting movement generates balanced debit and credit journal legs grouped by entry groups. Immutability and balance invariants are validated by the `PostingEngineService` prior to database commit.
2. **Deterministic Locking (Deadlock Prevention)**:
   All multi-account operations (such as internal fund transfers between accounts A and B) order target account IDs in strictly ascending order (`ORDER BY account_id ASC FOR UPDATE`). This completely eliminates cyclic wait deadlock conditions, even during high-concurrency opposing transfers (A &rarr; B and B &rarr; A simultaneously).
3. **Strict Balance Invariant**:
   $$\text{Available Balance} = \text{Ledger Balance} - \text{Hold Balance}$$
   Transactions are rejected with `InsufficientFundsException` if the debited amount exceeds available balance, ensuring accounts can never go into unauthorized overdraft.
4. **Dynamic CodeLists via Redis**:
   Zero hardcoded status check constraints in the DB. Validations are enforced at the Java record/DTO layer via `@ValidCodeList(group = "...")` backed by local/distributed Redis cache with write-through warmup.
5. **Hot / Cold Transaction Ledger Separation**:
   Daily active operations write to hot journal tables (`TRX_JOURNAL_ACTIVE`, `JOURNAL_ENTRY_ACTIVE`) and are archived to cold storage (`_DONE`) during End-of-Day (EOD) batch processing, maintaining lightning-fast OLTP daytime queries.
6. **Decoupled Operational Calendar**:
   All financial mutations use the logical banking date from `SYSTEM_OPERATIONAL_STATUS.current_working_date` rather than system physical clocks (`LocalDate.now()`), allowing automated business day rollover and holiday processing.
7. **Server-Driven UI (SDUI)**:
   Transaction screens, dynamic fields, and multi-step workflows are driven by backend JSON form schemas (`FORM`, `FORM_SHORTCUT`), decoupling UI presentation from frontend releases.

---

## 📚 Architecture Deep Dives & Domain Specifications

For in-depth Architectural Decision Records (ADRs), sequence diagrams, and regulatory compliance specifications, refer to the documentation in [`docs/`](docs/):

| Document | Core Topic & Banking Standard |
| :--- | :--- |
| [Posting-Driven Architecture](docs/POSTING_DRIVEN_ARCHITECTURE.md) | Why banking engines enforce Posting-First instead of Balance-First mutations |
| [Transaction Lifecycle & Invariants](docs/TRANSACTION_LIFECYCLE.md) | End-to-end 12-step OLTP execution, distributed idempotency & locking order |
| [EOD Batch Pipeline](docs/EOD_BATCH_PIPELINE.md) | 8-step Spring Batch End-of-Day cycle, cutoff freeze & GL reconciliation |
| [Waterfall Repayment Mechanism](docs/WATERFALL_REPAYMENT_MECHANISM.md) | Statutory debt recovery priority conforming to SBV Circular 39/2016/TT-NHNN |
| [Pluggable Multi-Charge IFC Pricing Engine](docs/IFC_PRICING_ENGINE_ARCHITECTURE.md) | Decoupled interest and fee pricing rules for deposit & lending products |
| [Dynamic Form Engine (SDUI ADR)](docs/DYNAMIC_FORM_ENGINE_DESIGN_DECISION.md) | Architectural rationale behind Server-Driven UI at Core Banking scale |
| [Core Banking Portfolio Showcase](docs/PORTFOLIO_CORE_BANKING_SHOWCASE.md) | Executive summary & benchmark against Temenos T24, Oracle FLEXCUBE & Neptune CBS |

---

## 🛠️ Tech Stack

### Backend
- **Java**: 21 LTS (Virtual Threads enabled)
- **Framework**: Spring Boot 3.3.4 (Spring Data JPA, Spring Batch, Spring Modulith)
- **Database**: Oracle Database 19c Enterprise (`COREBANKING_PDB`)
- **Cache**: Redis 7.x (`localhost:6379`)
- **Database Migrations**: Flyway (23 versioned SQL migrations)
- **Build Tool**: Apache Maven (Wrapper included)

### Frontend (Teller Portal)
- **Framework**: React 18.3
- **Build Tool**: Vite 5.4
- **Styling**: Tailwind CSS 3.4
- **Icons**: Lucide React
- **Architecture**: Single Page Application (SPA) embedded into Spring Boot static resources

---

## 🖥️ Teller Portal & Frontend Architecture

The frontend is housed in the [`frontend/`](frontend/README.md) directory and packaged into Spring Boot's static distribution.

Key frontend capabilities:
- **Server-Driven Dynamic Forms**: Dynamic form generator rendering fields (`TEXT`, `CURRENCY`, `DATE`, `CODELIST_SELECT`, `ACCOUNT_LOOKUP`) and running real-time workflows (`CALCULATE_TRANSFER_FEE`, account validation).
- **In-Form Accounting Leg Breakdown**: Displays debit/credit journal legs inside the transaction form upon completion for immediate audit.
- **Spotlight Command Palette (`Ctrl + K`)**: Keyboard-driven quick-launcher for navigating all core banking forms and catalogues.
- **Multi-Tab Workspace**: Chrome-style tabbed navigation allowing tellers to open multiple customer transactions simultaneously.
- **EOD Accounting Cockpit**: Real-time operations console to execute the 8-step End-of-Day batch cycle and inspect GL vs Sub-Ledger Trial Balance reconciliation.
- **Polymorphic Banking Vouchers**: Clean black-and-white receipts for Cash Deposit, Cash Withdrawal, Fund Transfer, Loan Origination, and Account Opening.

---

## 📂 Project Structure

```
CoreBanking/
├── pom.xml                           # Maven dependencies and build plugins
├── mvnw.cmd                          # Maven wrapper for Windows
├── docs/                             # Architecture specifications & Domain ADRs
├── frontend/                         # Modern Teller Portal SPA (React 18 + Vite)
│   ├── src/
│   │   ├── components/engine/        # SDUI Form Engine & Field Renderers
│   │   ├── components/workspace/     # Multi-Tab Container & Tab Navigation
│   │   ├── pages/                    # EOD Cockpit & Lending Console
│   │   └── services/                 # Axios API clients for CBS endpoints
│   └── package.json
└── src/
    ├── main/
    │   ├── java/com/cbs/
    │   │   ├── CoreBankingApplication.java
    │   │   ├── accounting/           # Posting Engine, Chart of Accounts, Account Resolver
    │   │   ├── customer/             # CIF management & KYC
    │   │   ├── deposit/              # CASA Deposit accounts, holds & interest rules
    │   │   ├── lending/              # Loan contracts, schedules, disbursement & repayment
    │   │   ├── transaction/          # Transfer, Deposit, Withdraw, Reversals, Idempotency
    │   │   ├── eod/                  # 8-step Spring Batch EOD Pipeline & Trial Balance
    │   │   ├── form/                 # Server-Driven UI (SDUI) form catalog & shortcuts
    │   │   ├── admin/                # Bank calendar, working date & cache controls
    │   │   └── common/               # Redis config, dynamic CodeLists & custom exceptions
    │   └── resources/
    │       ├── application.properties# HikariCP, JPA, Redis & Oracle datasource config
    │       ├── static/               # Production frontend build assets
    │       └── db/migration/         # Flyway SQL migrations (V1 to V23)
    └── test/
        └── java/com/cbs/
            ├── accounting/service/   # Unit tests: Posting Engine & Account Resolver
            ├── transaction/service/  # Unit tests: Deterministic Locking & Idempotency
            ├── lending/service/      # Unit tests: Amortization & Repayment Waterfall
            ├── eod/service/          # Unit tests: Balance Snapshots & Interest Accrual
            ├── stress/               # Virtual Thread Concurrency Stress Test
            └── transaction/          # Deadlock Concurrency & Integration Tests
```

---

## 🧪 Test Suite & Invariant Verification

The project includes unit tests, concurrency integration tests, and stress test suites validating core banking invariants.

### 1. Unit Tests (Isolated Mockito Suites)
Unit tests execute fast in-memory without requiring external database or Redis infrastructure:
- **Posting Engine Double-Entry Balancing** (`PostingEngineServiceTest`):
  Validates that every financial transaction (deposit, withdrawal, 2-leg/4-leg transfers) generates strictly balanced journal legs ($\sum D == \sum C$). Validates that unbalanced rules throw `UnbalancedTransactionException`.
- **Deterministic Row Locking & Deadlock Prevention** (`DeterministicLockingUnitTest`):
  Validates Invariant 3: Verifies that multi-account transfers always acquire pessimistic locks in ascending account ID order (`ORDER BY account_id ASC`) regardless of transfer direction. Validates self-transfer rejection and multi-leg reversal locking deduplication.
- **Idempotency & Thundering Herd Guard** (`IdempotencyServiceTest`):
  Validates request deduplication across Hot/Cold storage and Redis distributed lock protection.
- **Account Resolution Matrix** (`AccountResolverServiceTest`):
  Validates 3-tier fallback resolution (Account-specific &rarr; Product-specific &rarr; Default system GL).

Run all unit tests:
```powershell
.\mvnw.cmd test "-Dtest=*UnitTest,*ServiceTest"
```

Run specific core engine tests:
```powershell
.\mvnw.cmd test "-Dtest=PostingEngineServiceTest,DeterministicLockingUnitTest"
```

### 2. Concurrency & Deadlock Integration Tests
- **Deadlock Opposing Transfers** (`DeadlockConcurrencyIntegrationTest`):
  Executes 50 simultaneous opposing transfers (25 from Account A &rarr; B and 25 from B &rarr; A) using thread pools. Asserts that 100% of transactions complete with **0 deadlocks** and total system balance is strictly conserved.
- **Virtual Thread Concurrency Stress** (`VirtualThreadConcurrencyStressTest`):
  Fires 100+ concurrent financial transactions over Java 21 Virtual Threads (`Executors.newVirtualThreadPerTaskExecutor()`) to verify throughput, HikariCP connection pool stability, and balance consistency under load.

Run concurrency tests:
```powershell
.\mvnw.cmd test "-Dtest=DeadlockConcurrencyIntegrationTest"
```

---

## 🚀 Getting Started

### Prerequisites
- **JDK 21+** (Eclipse Adoptium Temurin 21 recommended)
- **Oracle Database 19c** running on `localhost:1521` (PDB: `COREBANKING_PDB`, user: `cbs_app`)
- **Redis 7.x** running on `localhost:6379`
- **Node.js 18+** & **npm** (for frontend development)

### Database Setup
1. Create the pluggable database `COREBANKING_PDB` and user `cbs_app` with password `cbs_password`.
2. Spring Boot automatically applies all versioned Flyway migrations (`V1` to `V23`) upon startup.

### Running Backend & Teller Portal
```powershell
# Run the Spring Boot application (includes embedded UI on port 8080)
.\mvnw.cmd spring-boot:run
```
Once started:
- **Teller Portal UI**: Open `http://localhost:8080` in your web browser.
- **REST APIs**: Available under `http://localhost:8080/api/v1/`.

### Running Frontend in Dev Mode (with Hot-Reload)
```powershell
cd frontend
npm install
npm run dev
```
Dev server starts on `http://localhost:5173` with Vite HMR and auto-proxies API requests to `http://localhost:8080`.

# 🏦 Core Banking System (Mini Core Engine)

> Enterprise Mini Core Banking Engine built with Java 21, Spring Boot 3.3.4, Oracle Database 19c, and Redis.  
> Architecture: **Modular Monolith** with strict domain boundaries and double-entry bookkeeping guarantees.

---

## 📌 Architecture Highlights

1. **Inviolable Double-Entry Bookkeeping**:
   $$\sum \text{Debit} == \sum \text{Credit}$$
   Every accounting leg is immutable and validated by the Posting Engine before commit.
2. **Deterministic Locking (Deadlock Prevention)**:
   Always locks accounts `ORDER BY account_id ASC FOR UPDATE` during multi-account movements.
3. **Strict Balance Invariant**:
   $$\text{Available Balance} = \text{Ledger Balance} - \text{Hold Balance}$$
4. **Dynamic CodeList via Redis**:
   Zero hardcoded status check constraints in the DB. Validations are enforced at the Java record/DTO layer via `@ValidCodeList(group = "...")` backed by local/distributed Redis cache.
5. **Hot / Cold Transaction Ledger Separation**:
   Daily active operations write to hot journal tables (`TRX_JOURNAL_ACTIVE`, `JOURNAL_ENTRY_ACTIVE`) and are archived to cold storage (`_DONE`) during End-of-Day (EOD) processing.
6. **Decoupled Operational Calendar**:
   All business transactions use the logical banking date from `SYSTEM_OPERATIONAL_STATUS.current_working_date` rather than system physical clocks.

---

## 🛠️ Tech Stack

- **Java**: 21 LTS (Temurin, Virtual Threads enabled)
- **Framework**: Spring Boot 3.3.4 (Spring Modulith, Spring Data JPA, Spring Batch)
- **Database**: Oracle Database 19c (`COREBANKING_PDB`)
- **Cache**: Redis 7.x (`localhost:6379`)
- **Database Migrations**: Flyway
- **Build Tool**: Apache Maven (Wrapper included)

---

## 📂 Project Structure

```
src/main/java/com/cbs/
├── CoreBankingApplication.java
├── common/
│   ├── codelist/         # Dynamic CodeList entities, Redis caching & @ValidCodeList
│   ├── config/           # Redis & JPA configuration
│   └── exception/        # Domain business exceptions
├── accounting/           # Posting Engine, Chart of Accounts, Account Resolver
├── customer/             # Customer CIF & KYC management
├── account/              # CASA Deposit accounts, holds & earmarks
├── batch/                # End-of-Day (EOD) Spring Batch processing
└── admin/                # Operational calendar & working date controls
```

---

## 🚀 Getting Started

### Prerequisites
- JDK 21+
- Oracle 19c running on `localhost:1521` (PDB: `COREBANKING_PDB`)
- Redis running on `localhost:6379`

### Run Tests
```powershell
.\mvnw.cmd test
```

### Run Application
```powershell
.\mvnw.cmd spring-boot:run
```

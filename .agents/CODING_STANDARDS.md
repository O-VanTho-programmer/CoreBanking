# Java 21 & Spring Boot 3 Coding Standards

## 1. Java 21 Modern Language Features
- **Records**: Use `record` for all DTOs, API Requests/Responses, Commands, Queries, and Value Objects.
- **Pattern Matching & Sealed Interfaces**: Use for transaction status workflows and accounting rule evaluation.
- **Virtual Threads**: Enable Project Loom via `spring.threads.virtual.enabled=true` for handling high-concurrency I/O bound banking queries.
- **Null Safety**: Avoid returning `null`. Use `Optional<T>` for query results.

---

## 2. Mathematical Rigor & BigDecimal Rules
- **NEVER** use `double` or `float` for monetary calculations.
- Always instantiate `BigDecimal` from String or integer types:
  ```java
  BigDecimal.valueOf(100L); // OK
  new BigDecimal("100.5000"); // OK
  new BigDecimal(100.5); // FORBIDDEN (creates floating point binary drift)
  ```
- All calculations must explicitly specify scale (4 places) and RoundingMode:
  ```java
  amount.setScale(4, RoundingMode.HALF_UP);
  ```

---

## 3. Package Structure (Spring Modulith Conventions)
```
com.cbs
├── admin          # Bank calendar, operational date, system controls
├── customer       # CIF, KYC, Customer profiles
├── account        # CASA accounts, hold balances, product catalog
├── accounting     # Double-entry posting engine, rule matrix, account mapping
├── batch          # EOD accrual, capitalization, data archival tasks
└── common         # CodeList service/cache, Money utils, base entities
```
- Modules must communicate through public service interfaces, not by directly accessing other modules' private repository tables.

---

## 4. Concurrency & Transaction Management
- Use `@Transactional(isolation = Isolation.READ_COMMITTED)` as default.
- For financial balance mutations, use explicit pessimistic write locking:
  ```java
  @Lock(LockModeType.PESSIMISTIC_WRITE)
  @Query("SELECT a FROM Account a WHERE a.accountId IN :ids ORDER BY a.accountId ASC")
  List<Account> findAndLockAccountsByIds(@Param("ids") List<Long> ids);
  ```

# Verification & Testing Strategy

## 1. Automated Testing Pyramid
- **Unit Tests**:
  - Double-Entry Equation tests: verify that multi-legged postings sum to exactly zero.
  - Math & Rounding tests: edge cases around leap years, sub-cent rounding, day count basis (ACT/365 vs ACT/360).
  - Rule Matrix interpreter tests: assert that rule definitions map correctly to balanced legs.
- **Integration Tests**:
  - Spring Boot Test with Testcontainers / H2 Oracle Mode.
  - Flyway migration verification.
  - Deterministic locking tests: run parallel concurrent threads performing bidirectional transfers ($A \rightarrow B$ and $B \rightarrow A$) and verify zero deadlocks.
- **Modulith Architecture Tests**:
  - `ApplicationModules.of(CoreBankingApplication.class).verify()` to enforce strict bounded-context isolation.

---

## 2. Standard Commands for Verification
```powershell
# Run all tests
.\mvnw.cmd test

# Compile without running tests
.\mvnw.cmd compile -DskipTests

# Run Flyway migration check
.\mvnw.cmd flyway:info
```

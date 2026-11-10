package com.cbs.account.repository;

import com.cbs.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long>, JpaSpecificationExecutor<Account> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("SELECT a.accountId FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    List<Account> findByCustomerId(Long customerId);

    List<Account> findByStatus(String status);

    boolean existsByAccountNumber(String accountNumber);

    /**
     * Acquires pessimistic write lock on single account for mutations.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findByIdForUpdate(@Param("id") Long id);

    /**
     * Invariant 3: Deterministic row locking ordered by account_id ASC to prevent deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId IN :ids ORDER BY a.accountId ASC")
    List<Account> findAndLockAccountsByIds(@Param("ids") List<Long> ids);

    @Query("SELECT COALESCE(SUM(a.ledgerBalance), 0) FROM Account a WHERE a.glCode = :glCode")
    java.math.BigDecimal sumLedgerBalanceByGlCode(@Param("glCode") String glCode);

    @Query("""
        SELECT a FROM Account a
        WHERE a.status = 'ACTIVE'
          AND a.productType = 'FD'
          AND a.maturityDate <= :workingDate
        ORDER BY a.accountId ASC
    """)
    List<Account> findMaturingFdAccounts(@Param("workingDate") java.time.LocalDate workingDate);
}

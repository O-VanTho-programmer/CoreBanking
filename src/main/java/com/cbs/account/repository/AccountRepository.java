package com.cbs.account.repository;

import com.cbs.account.domain.Account;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountRepository extends JpaRepository<Account, Long> {

    Optional<Account> findByAccountNumber(String accountNumber);

    @Query("SELECT a.accountId FROM Account a WHERE a.accountNumber = :accountNumber")
    Optional<Long> findIdByAccountNumber(@Param("accountNumber") String accountNumber);

    List<Account> findByCustomerId(Long customerId);

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
}

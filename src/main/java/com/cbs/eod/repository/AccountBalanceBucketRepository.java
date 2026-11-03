package com.cbs.eod.repository;

import com.cbs.eod.domain.AccountBalanceBucket;
import com.cbs.eod.domain.AccountBalanceBucketId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountBalanceBucketRepository extends JpaRepository<AccountBalanceBucket, AccountBalanceBucketId> {

    @Modifying
    @Query("DELETE FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    void deleteByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT COUNT(b) > 0 FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    boolean existsByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT b FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    List<AccountBalanceBucket> findByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT b FROM AccountBalanceBucket b WHERE b.id.accountId = :accountId ORDER BY b.id.workingDate DESC")
    List<AccountBalanceBucket> findByAccountIdOrderByWorkingDateDesc(@Param("accountId") Long accountId);

    @Query("SELECT COALESCE(SUM(b.dailyDebitSum), 0) FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    java.math.BigDecimal sumDailyDebitByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT COALESCE(SUM(b.dailyCreditSum), 0) FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    java.math.BigDecimal sumDailyCreditByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT COALESCE(SUM(b.closingBalance), 0) FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    java.math.BigDecimal sumClosingBalanceByWorkingDate(@Param("workingDate") LocalDate workingDate);
}

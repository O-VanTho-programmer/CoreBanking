package com.cbs.eod.repository;

import com.cbs.eod.domain.AccountBalanceBucket;
import com.cbs.eod.domain.AccountBalanceBucketId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountBalanceBucketRepository extends JpaRepository<AccountBalanceBucket, AccountBalanceBucketId> {

    @Query("SELECT COUNT(b) > 0 FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    boolean existsByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT b FROM AccountBalanceBucket b WHERE b.id.workingDate = :workingDate")
    List<AccountBalanceBucket> findByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT b FROM AccountBalanceBucket b WHERE b.id.accountId = :accountId ORDER BY b.id.workingDate DESC")
    List<AccountBalanceBucket> findByAccountIdOrderByWorkingDateDesc(@Param("accountId") Long accountId);
}

package com.cbs.transaction.repository;

import com.cbs.transaction.domain.JournalEntryDone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryDoneRepository extends JpaRepository<JournalEntryDone, Long> {

    List<JournalEntryDone> findByTrxId(Long trxId);

    List<JournalEntryDone> findByAccountId(Long accountId);

    @Query("SELECT j.accountId, j.drCr, COALESCE(SUM(j.amount), 0) " +
           "FROM JournalEntryDone j " +
           "WHERE j.valueDate = :valueDate AND j.accountId IS NOT NULL " +
           "GROUP BY j.accountId, j.drCr")
    List<Object[]> aggregateDailyTurnoverByValueDate(@Param("valueDate") LocalDate valueDate);

    @Query("SELECT j.drCr, COALESCE(SUM(j.amount), 0) FROM JournalEntryDone j WHERE j.valueDate = :valueDate GROUP BY j.drCr")
    List<Object[]> sumTurnoverByDrCr(@Param("valueDate") LocalDate valueDate);

    @Query("SELECT j.drCr, COALESCE(SUM(j.amount), 0) FROM JournalEntryDone j WHERE j.valueDate = :valueDate AND j.glCode = :glCode GROUP BY j.drCr")
    List<Object[]> sumTurnoverByGlCodeAndDrCr(@Param("valueDate") LocalDate valueDate, @Param("glCode") String glCode);
}

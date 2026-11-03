package com.cbs.transaction.repository;

import com.cbs.transaction.domain.JournalEntryActive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface JournalEntryActiveRepository extends JpaRepository<JournalEntryActive, Long> {

    List<JournalEntryActive> findByTrxIdOrderByEntryGroupAscEntryIndexAsc(Long trxId);

    List<JournalEntryActive> findByAccountId(Long accountId);

    List<JournalEntryActive> findByTrxIdIn(List<Long> trxIds);

    @Modifying
    @Query("DELETE FROM JournalEntryActive j WHERE j.trxId IN :trxIds")
    void deleteByTrxIdIn(@Param("trxIds") List<Long> trxIds);

    /**
     * Aggregates total debit and credit amounts per customer account for a specific value date.
     * Returns List of Object[]: [Long accountId, String drCr, BigDecimal sumAmount]
     */
    @Query("SELECT j.accountId, j.drCr, COALESCE(SUM(j.amount), 0) " +
           "FROM JournalEntryActive j " +
           "WHERE j.valueDate = :valueDate AND j.accountId IS NOT NULL " +
           "GROUP BY j.accountId, j.drCr")
    List<Object[]> aggregateDailyTurnoverByValueDate(@Param("valueDate") LocalDate valueDate);

    @Query("SELECT j.drCr, COALESCE(SUM(j.amount), 0) FROM JournalEntryActive j WHERE j.valueDate = :valueDate GROUP BY j.drCr")
    List<Object[]> sumTurnoverByDrCr(@Param("valueDate") LocalDate valueDate);

    @Query("SELECT j.drCr, COALESCE(SUM(j.amount), 0) FROM JournalEntryActive j WHERE j.valueDate = :valueDate AND j.glCode = :glCode GROUP BY j.drCr")
    List<Object[]> sumTurnoverByGlCodeAndDrCr(@Param("valueDate") LocalDate valueDate, @Param("glCode") String glCode);
}

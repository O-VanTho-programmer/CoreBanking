package com.cbs.transaction.repository;

import com.cbs.transaction.domain.TrxJournalActive;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface TrxJournalActiveRepository extends JpaRepository<TrxJournalActive, Long> {

    Optional<TrxJournalActive> findByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);

    Optional<TrxJournalActive> findByTrxNumber(String trxNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TrxJournalActive t WHERE t.trxNumber = :trxNumber")
    Optional<TrxJournalActive> findByTrxNumberForUpdate(@Param("trxNumber") String trxNumber);

    boolean existsByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);

    @Query("SELECT COUNT(t) FROM TrxJournalActive t WHERE t.workingDate <= :workingDate AND t.status = 'IN_FLIGHT'")
    long countInFlightOnOrBefore(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT t FROM TrxJournalActive t WHERE t.workingDate <= :workingDate AND t.status IN ('COMPLETED', 'FAILED') ORDER BY t.trxId ASC")
    List<TrxJournalActive> findEligibleForArchival(@Param("workingDate") LocalDate workingDate);

    @Modifying
    @Query("UPDATE TrxJournalActive t SET t.originalTrxId = NULL WHERE t.trxId IN :trxIds")
    void clearOriginalTrxIds(@Param("trxIds") List<Long> trxIds);
}

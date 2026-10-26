package com.cbs.transaction.repository;

import com.cbs.transaction.domain.TrxJournalActive;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrxJournalActiveRepository extends JpaRepository<TrxJournalActive, Long> {

    Optional<TrxJournalActive> findByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);

    Optional<TrxJournalActive> findByTrxNumber(String trxNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM TrxJournalActive t WHERE t.trxNumber = :trxNumber")
    Optional<TrxJournalActive> findByTrxNumberForUpdate(@Param("trxNumber") String trxNumber);

    boolean existsByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);
}

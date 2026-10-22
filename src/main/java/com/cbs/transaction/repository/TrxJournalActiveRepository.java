package com.cbs.transaction.repository;

import com.cbs.transaction.domain.TrxJournalActive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrxJournalActiveRepository extends JpaRepository<TrxJournalActive, Long> {

    Optional<TrxJournalActive> findByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);

    Optional<TrxJournalActive> findByTrxNumber(String trxNumber);

    boolean existsByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);
}

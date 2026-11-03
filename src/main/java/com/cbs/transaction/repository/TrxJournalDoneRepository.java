package com.cbs.transaction.repository;

import com.cbs.transaction.domain.TrxJournalDone;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface TrxJournalDoneRepository extends JpaRepository<TrxJournalDone, Long> {

    Optional<TrxJournalDone> findByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);

    boolean existsByChannelIdAndIdempotencyKey(String channelId, String idempotencyKey);
}

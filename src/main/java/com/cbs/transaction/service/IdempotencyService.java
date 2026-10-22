package com.cbs.transaction.service;

import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.domain.TrxJournalDone;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.Optional;

/**
 * Two-Tier Composite Idempotency Service (Invariant 5).
 * Checks Redis -> Hot Ledger (TRX_JOURNAL_ACTIVE) -> Cold Archive (TRX_JOURNAL_DONE).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String CACHE_KEY_PREFIX = "cbs:idemp:";
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    private final TrxJournalActiveRepository activeRepository;
    private final TrxJournalDoneRepository doneRepository;
    private final StringRedisTemplate stringRedisTemplate;
    private final ObjectMapper objectMapper;

    /**
     * Checks if a transaction with (channelId, idempotencyKey) was already submitted or processed.
     *
     * @return Optional containing previous TransactionReceipt if completed
     * @throws IllegalStateException if the transaction is currently IN_FLIGHT
     */
    public Optional<TransactionReceipt> checkExisting(String channelId, String idempotencyKey) {
        String cacheKey = buildCacheKey(channelId, idempotencyKey);

        // 1. Check Redis L1 Fast Filter
        try {
            String cachedJson = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedJson != null && !cachedJson.isBlank()) {
                log.info("Idempotency HIT in Redis for [{}]: replaying previous receipt", cacheKey);
                TransactionReceipt receipt = objectMapper.readValue(cachedJson, TransactionReceipt.class);
                return Optional.of(receipt);
            }
        } catch (Exception e) {
            log.warn("Redis unavailable during idempotency check for [{}]: {}", cacheKey, e.getMessage());
        }

        // 2. Check Hot Table (TRX_JOURNAL_ACTIVE)
        Optional<TrxJournalActive> hotOpt = activeRepository.findByChannelIdAndIdempotencyKey(channelId, idempotencyKey);
        if (hotOpt.isPresent()) {
            TrxJournalActive hot = hotOpt.get();
            if ("IN_FLIGHT".equals(hot.getStatus())) {
                log.warn("Transaction [channel={}, key={}] is currently IN_FLIGHT", channelId, idempotencyKey);
                throw new IllegalStateException("Transaction is currently processing (IN_FLIGHT). Please retry shortly.");
            }
            log.info("Idempotency HIT in Hot Table for [channel={}, key={}]", channelId, idempotencyKey);
            TransactionReceipt receipt = mapHotToReceipt(hot);
            cacheReceipt(cacheKey, receipt);
            return Optional.of(receipt);
        }

        // 3. Check Cold Table (TRX_JOURNAL_DONE)
        Optional<TrxJournalDone> doneOpt = doneRepository.findByChannelIdAndIdempotencyKey(channelId, idempotencyKey);
        if (doneOpt.isPresent()) {
            TrxJournalDone done = doneOpt.get();
            log.info("Idempotency HIT in Cold Table for [channel={}, key={}]", channelId, idempotencyKey);
            TransactionReceipt receipt = mapDoneToReceipt(done);
            cacheReceipt(cacheKey, receipt);
            return Optional.of(receipt);
        }

        return Optional.empty();
    }

    /**
     * Records completed transaction receipt into Redis cache.
     */
    public void recordCompletion(String channelId, String idempotencyKey, TransactionReceipt receipt) {
        String cacheKey = buildCacheKey(channelId, idempotencyKey);
        cacheReceipt(cacheKey, receipt);
    }

    private void cacheReceipt(String cacheKey, TransactionReceipt receipt) {
        try {
            String json = objectMapper.writeValueAsString(receipt);
            stringRedisTemplate.opsForValue().set(cacheKey, json, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to cache idempotency receipt in Redis [{}]: {}", cacheKey, e.getMessage());
        }
    }

    private String buildCacheKey(String channelId, String idempotencyKey) {
        return CACHE_KEY_PREFIX + channelId + ":" + idempotencyKey;
    }

    private TransactionReceipt mapHotToReceipt(TrxJournalActive hot) {
        return TransactionReceipt.builder()
                .trxId(hot.getTrxId())
                .trxNumber(hot.getTrxNumber())
                .channelId(hot.getChannelId())
                .idempotencyKey(hot.getIdempotencyKey())
                .trxCode(hot.getTrxCode())
                .workingDate(hot.getWorkingDate())
                .status(hot.getStatus())
                .currency("VND")
                .legs(Collections.emptyList())
                .timestamp(hot.getCreatedAt())
                .build();
    }

    private TransactionReceipt mapDoneToReceipt(TrxJournalDone done) {
        return TransactionReceipt.builder()
                .trxId(done.getTrxId())
                .trxNumber(done.getTrxNumber())
                .channelId(done.getChannelId())
                .idempotencyKey(done.getIdempotencyKey())
                .trxCode(done.getTrxCode())
                .workingDate(done.getWorkingDate())
                .status(done.getStatus())
                .currency("VND")
                .legs(Collections.emptyList())
                .timestamp(done.getCreatedAt())
                .build();
    }
}

package com.cbs.transaction.service;

import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
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
 * Checks Redis L1 Fast Filter (24h TTL) -> Hot Ledger (TRX_JOURNAL_ACTIVE).
 * Strictly avoids slow disk I/O queries on the cold historical archive (TRX_JOURNAL_DONE)
 * during daytime OLTP execution, guaranteeing maximum throughput and preventing buffer cache pollution.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class IdempotencyService {

    public static final String CACHE_KEY_PREFIX = "cbs:idemp:";
    private static final Duration CACHE_TTL = Duration.ofHours(24);

    private final TrxJournalActiveRepository activeRepository;
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

        // 1. Check Redis L1 Fast Filter (24-hour TTL)
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

        // If not found in Redis or Hot Table, transaction is brand new for the daytime window.
        // Cold archive (TRX_JOURNAL_DONE) is never queried during daytime OLTP to eliminate I/O overhead.
        return Optional.empty();
    }

    /**
     * Records completed transaction receipt into Redis cache.
     */
    public void recordCompletion(String channelId, String idempotencyKey, TransactionReceipt receipt) {
        String cacheKey = buildCacheKey(channelId, idempotencyKey);
        cacheReceipt(cacheKey, receipt);
    }

    /**
     * Acquires a transient distributed lock in Redis to guard against thundering herd.
     */
    public boolean acquireLock(String channelId, String idempotencyKey) {
        String lockKey = buildLockKey(channelId, idempotencyKey);
        try {
            Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(lockKey, "LOCKED", Duration.ofSeconds(15));
            return Boolean.TRUE.equals(ok);
        } catch (Exception e) {
            log.warn("Redis error acquiring idempotency lock [{}]: {}", lockKey, e.getMessage());
            return true;
        }
    }

    /**
     * Releases transient distributed lock in Redis.
     */
    public void releaseLock(String channelId, String idempotencyKey) {
        String lockKey = buildLockKey(channelId, idempotencyKey);
        try {
            stringRedisTemplate.delete(lockKey);
        } catch (Exception e) {
            log.warn("Redis error releasing idempotency lock [{}]: {}", lockKey, e.getMessage());
        }
    }

    /**
     * Waits for an in-flight transaction with the same idempotency key to complete and produce a receipt.
     */
    public Optional<TransactionReceipt> waitForCompletion(String channelId, String idempotencyKey, Duration timeout) {
        long endNanos = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < endNanos) {
            Optional<TransactionReceipt> existing = checkExisting(channelId, idempotencyKey);
            if (existing.isPresent()) {
                return existing;
            }
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Optional.empty();
            }
        }
        return checkExisting(channelId, idempotencyKey);
    }

    private String buildLockKey(String channelId, String idempotencyKey) {
        return "cbs:idemp:lock:" + channelId + ":" + idempotencyKey;
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
                .originalTrxId(hot.getOriginalTrxId())
                .reversalReason(hot.getReversalReason())
                .legs(Collections.emptyList())
                .timestamp(hot.getCreatedAt())
                .build();
    }
}

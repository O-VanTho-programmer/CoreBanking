package com.cbs.transaction.service;

import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class IdempotencyServiceTest {

    @Mock
    private TrxJournalActiveRepository activeRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ObjectMapper objectMapper;

    @InjectMocks
    private IdempotencyService idempotencyService;

    private static final String CHANNEL_ID = "MB";
    private static final String IDEMPOTENCY_KEY = "test-uuid-12345";
    private static final String CACHE_KEY = "cbs:idemp:MB:test-uuid-12345";

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("L1 Fast Path: Cached receipt in Redis (24h TTL) returned directly without hitting database")
    void shouldReturnReceiptFromRedisWhenCacheHit() throws Exception {
        TransactionReceipt mockReceipt = TransactionReceipt.builder()
                .trxId(100L)
                .trxNumber("TRX-20261022-00000001")
                .channelId(CHANNEL_ID)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .status("COMPLETED")
                .build();

        when(valueOperations.get(CACHE_KEY)).thenReturn("{\"trxId\":100}");
        when(objectMapper.readValue("{\"trxId\":100}", TransactionReceipt.class)).thenReturn(mockReceipt);

        Optional<TransactionReceipt> result = idempotencyService.checkExisting(CHANNEL_ID, IDEMPOTENCY_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().trxId()).isEqualTo(100L);
        assertThat(result.get().trxNumber()).isEqualTo("TRX-20261022-00000001");
        verifyNoInteractions(activeRepository);
    }

    @Test
    @DisplayName("L2 Hot Ledger: Found in TRX_JOURNAL_ACTIVE -> returns receipt and populates Redis with 24h TTL")
    void shouldReturnReceiptFromHotTableWhenRedisMisses() throws JsonProcessingException {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        TrxJournalActive active = TrxJournalActive.builder()
                .trxId(200L)
                .trxNumber("TRX-20261022-00000002")
                .channelId(CHANNEL_ID)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .trxCode("TRF_INTERNAL")
                .workingDate(LocalDate.of(2026, 10, 22))
                .status("COMPLETED")
                .createdAt(OffsetDateTime.now())
                .build();

        when(activeRepository.findByChannelIdAndIdempotencyKey(CHANNEL_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(active));
        when(objectMapper.writeValueAsString(any())).thenReturn("{\"trxId\":200}");

        Optional<TransactionReceipt> result = idempotencyService.checkExisting(CHANNEL_ID, IDEMPOTENCY_KEY);

        assertThat(result).isPresent();
        assertThat(result.get().trxId()).isEqualTo(200L);
        assertThat(result.get().status()).isEqualTo("COMPLETED");

        verify(valueOperations).set(eq(CACHE_KEY), eq("{\"trxId\":200}"), eq(Duration.ofHours(24)));
    }

    @Test
    @DisplayName("L2 Hot Ledger: Found IN_FLIGHT -> throws IllegalStateException (concurrent in-progress)")
    void shouldThrowExceptionWhenHotTransactionIsInFlight() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);

        TrxJournalActive active = TrxJournalActive.builder()
                .trxId(201L)
                .trxNumber("TRX-20261022-00000003")
                .channelId(CHANNEL_ID)
                .idempotencyKey(IDEMPOTENCY_KEY)
                .status("IN_FLIGHT")
                .build();

        when(activeRepository.findByChannelIdAndIdempotencyKey(CHANNEL_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.of(active));

        assertThatThrownBy(() -> idempotencyService.checkExisting(CHANNEL_ID, IDEMPOTENCY_KEY))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_FLIGHT");
    }

    @Test
    @DisplayName("Daytime Fast Path: When Redis and Hot Table miss, returns empty immediately without querying cold archive")
    void shouldReturnEmptyWhenKeyNotFoundInHotTable() {
        when(valueOperations.get(CACHE_KEY)).thenReturn(null);
        when(activeRepository.findByChannelIdAndIdempotencyKey(CHANNEL_ID, IDEMPOTENCY_KEY))
                .thenReturn(Optional.empty());

        Optional<TransactionReceipt> result = idempotencyService.checkExisting(CHANNEL_ID, IDEMPOTENCY_KEY);

        assertThat(result).isEmpty();
        verify(activeRepository, times(1)).findByChannelIdAndIdempotencyKey(CHANNEL_ID, IDEMPOTENCY_KEY);
    }
}

package com.cbs.accounting.service;

import com.cbs.accounting.repository.AccountMappingDetailRepository;
import com.cbs.common.exception.AccountMappingNotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AccountResolverServiceTest {

    @Mock
    private AccountMappingDetailRepository detailRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @InjectMocks
    private AccountResolverService resolverService;

    @BeforeEach
    void setUp() {
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    @DisplayName("resolvePhysicalGl() returns cached GL code immediately on cache hit")
    void shouldReturnCachedGlOnCacheHit() {
        String cacheKey = AccountResolverService.CACHE_KEY_PREFIX + "SYS_CASH_VAULT:VN001:VND:CASA_VND";
        when(valueOperations.get(cacheKey)).thenReturn("1011");

        String result = resolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND");

        assertThat(result).isEqualTo("1011");
        verifyNoInteractions(detailRepository);
    }

    @Test
    @DisplayName("resolvePhysicalGl() queries DB and populates cache on cache miss")
    void shouldQueryDbAndCacheOnMiss() {
        String cacheKey = AccountResolverService.CACHE_KEY_PREFIX + "SYS_CASH_VAULT:VN001:VND:CASA_VND";
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(detailRepository.findMatchingPhysicalGlCodes("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND"))
                .thenReturn(List.of("1011"));

        String result = resolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND");

        assertThat(result).isEqualTo("1011");
        verify(valueOperations).set(eq(cacheKey), eq("1011"), any(Duration.class));
    }

    @Test
    @DisplayName("resolvePhysicalGl() selects first match according to hierarchical ordering")
    void shouldSelectFirstMatchFromPrioritizedList() {
        String cacheKey = AccountResolverService.CACHE_KEY_PREFIX + "SYS_FEE_INCOME:VN001:VND:CASA_VND";
        when(valueOperations.get(cacheKey)).thenReturn(null);
        // List returned from ordered query: exact branch match comes first
        when(detailRepository.findMatchingPhysicalGlCodes("SYS_FEE_INCOME", "VN001", "VND", "CASA_VND"))
                .thenReturn(List.of("7111_SPECIFIC", "7111_ALL"));

        String result = resolverService.resolvePhysicalGl("SYS_FEE_INCOME", "VN001", "VND", "CASA_VND");

        assertThat(result).isEqualTo("7111_SPECIFIC");
    }

    @Test
    @DisplayName("resolvePhysicalGl() throws AccountMappingNotFoundException when DB returns no match")
    void shouldThrowExceptionWhenNoMappingFound() {
        String cacheKey = AccountResolverService.CACHE_KEY_PREFIX + "NON_EXISTENT:VN001:VND:CASA_VND";
        when(valueOperations.get(cacheKey)).thenReturn(null);
        when(detailRepository.findMatchingPhysicalGlCodes("NON_EXISTENT", "VN001", "VND", "CASA_VND"))
                .thenReturn(Collections.emptyList());

        assertThatThrownBy(() -> resolverService.resolvePhysicalGl("NON_EXISTENT", "VN001", "VND", "CASA_VND"))
                .isInstanceOf(AccountMappingNotFoundException.class)
                .hasMessageContaining("NON_EXISTENT");
    }

    @Test
    @DisplayName("resolvePhysicalGl() falls back gracefully to DB when Redis throws exception")
    void shouldFallbackToDbWhenRedisFails() {
        when(valueOperations.get(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis offline"));
        when(detailRepository.findMatchingPhysicalGlCodes("SYS_CASH_VAULT", "ALL", "VND", "ALL"))
                .thenReturn(List.of("1011"));

        String result = resolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VND");

        assertThat(result).isEqualTo("1011");
    }
}

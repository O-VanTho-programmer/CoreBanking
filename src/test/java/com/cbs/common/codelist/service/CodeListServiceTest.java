package com.cbs.common.codelist.service;

import com.cbs.common.codelist.repository.CodeListValueRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeListServiceTest {

    @Mock
    private CodeListValueRepository codeListValueRepository;

    @Mock
    private StringRedisTemplate stringRedisTemplate;

    @Mock
    private SetOperations<String, String> setOperations;

    @InjectMocks
    private CodeListService codeListService;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOperations);
    }

    @Test
    @DisplayName("isValid() should return true when code exists in Redis cache")
    void shouldReturnTrueWhenCodeExistsInRedis() {
        String group = "ACCT_STATUS";
        String value = "ACTIVE";
        String redisKey = CodeListService.KEY_PREFIX + group;

        when(stringRedisTemplate.hasKey(redisKey)).thenReturn(true);
        when(setOperations.isMember(redisKey, value)).thenReturn(true);

        boolean result = codeListService.isValid(group, value);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid() should return false when code is not in Redis cache")
    void shouldReturnFalseWhenCodeNotInRedis() {
        String group = "ACCT_STATUS";
        String value = "INVALID_STATUS";
        String redisKey = CodeListService.KEY_PREFIX + group;

        when(stringRedisTemplate.hasKey(redisKey)).thenReturn(true);
        when(setOperations.isMember(redisKey, value)).thenReturn(false);

        boolean result = codeListService.isValid(group, value);

        assertThat(result).isFalse();
    }

    @Test
    @DisplayName("isValid() should reload cache from DB on cache miss")
    void shouldReloadFromDbOnCacheMiss() {
        String group = "KYC_STATUS";
        String value = "VERIFIED";
        String redisKey = CodeListService.KEY_PREFIX + group;

        when(stringRedisTemplate.hasKey(redisKey)).thenReturn(false);
        when(codeListValueRepository.findActiveCodeValuesByCodeGroup(group))
                .thenReturn(List.of("PENDING", "VERIFIED", "REJECTED"));
        when(setOperations.isMember(redisKey, value)).thenReturn(true);

        boolean result = codeListService.isValid(group, value);

        assertThat(result).isTrue();
        verify(setOperations).add(eq(redisKey), any(String[].class));
    }

    @Test
    @DisplayName("isValid() should fallback to database when Redis throws exception")
    void shouldFallbackToDatabaseWhenRedisFails() {
        String group = "CUST_STATUS";
        String value = "ACTIVE";
        String redisKey = CodeListService.KEY_PREFIX + group;

        when(stringRedisTemplate.hasKey(redisKey))
                .thenThrow(new RedisConnectionFailureException("Connection refused"));
        when(codeListValueRepository.findActiveCodeValuesByCodeGroup(group))
                .thenReturn(List.of("ACTIVE", "SUSPENDED", "DECEASED"));

        boolean result = codeListService.isValid(group, value);

        assertThat(result).isTrue();
    }

    @Test
    @DisplayName("isValid() should return false for null or empty values")
    void shouldReturnFalseForNullOrEmptyValues() {
        assertThat(codeListService.isValid(null, "ACTIVE")).isFalse();
        assertThat(codeListService.isValid("ACCT_STATUS", null)).isFalse();
        assertThat(codeListService.isValid("ACCT_STATUS", "   ")).isFalse();
    }

    @Test
    @DisplayName("getValidCodes() should return cached set of codes")
    void shouldReturnValidCodesFromCache() {
        String group = "TRX_STATUS";
        String redisKey = CodeListService.KEY_PREFIX + group;

        when(setOperations.members(redisKey))
                .thenReturn(Set.of("IN_FLIGHT", "COMPLETED", "FAILED"));

        Set<String> codes = codeListService.getValidCodes(group);

        assertThat(codes).containsExactlyInAnyOrder("IN_FLIGHT", "COMPLETED", "FAILED");
    }
}

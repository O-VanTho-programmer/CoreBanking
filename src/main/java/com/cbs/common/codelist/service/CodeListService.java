package com.cbs.common.codelist.service;

import com.cbs.common.codelist.repository.CodeListValueRepository;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * High-performance CodeList Service backed by distributed Redis cache.
 * Implements Invariant 6: Status Validation via Dynamic CodeList.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class CodeListService {

    public static final String KEY_PREFIX = "cbs:codelist:";
    private static final Duration CACHE_TTL = Duration.ofHours(2);
    private static final String EMPTY_SENTINEL = "__EMPTY__";

    private final CodeListValueRepository codeListValueRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Warms up Redis cache on application bootstrap for all active code groups.
     */
    @PostConstruct
    public void warmupCache() {
        try {
            log.info("Warming up Redis cache for all active CodeLists...");
            List<String> groups = codeListValueRepository.findAllActiveCodeGroups();
            for (String group : groups) {
                refreshCache(group);
            }
            log.info("CodeList Redis cache warm-up completed successfully for {} groups.", groups.size());
        } catch (Exception e) {
            log.warn("Redis warm-up encountered an issue (will load on-demand): {}", e.getMessage());
        }
    }

    /**
     * Validates whether a given codeValue is active within the specified codeGroup.
     *
     * @param codeGroup the group identifier (e.g. "ACCT_STATUS", "KYC_STATUS")
     * @param codeValue the value to test (e.g. "ACTIVE", "BLOCKED")
     * @return true if active and valid, false otherwise
     */
    public boolean isValid(String codeGroup, String codeValue) {
        if (codeGroup == null || codeValue == null || codeValue.isBlank()) {
            return false;
        }

        try {
            String redisKey = KEY_PREFIX + codeGroup;
            Boolean hasKey = stringRedisTemplate.hasKey(redisKey);

            if (Boolean.FALSE.equals(hasKey)) {
                refreshCache(codeGroup);
            }

            Boolean isMember = stringRedisTemplate.opsForSet().isMember(redisKey, codeValue.trim());
            return Boolean.TRUE.equals(isMember);
        } catch (Exception e) {
            log.warn("Redis cache unavailable for isValid({}, {}). Falling back to Database query: {}",
                    codeGroup, codeValue, e.getMessage());
            return validateFromDatabase(codeGroup, codeValue);
        }
    }

    /**
     * Retrieves all valid active codes for a group.
     */
    public Set<String> getValidCodes(String codeGroup) {
        if (codeGroup == null || codeGroup.isBlank()) {
            return Collections.emptySet();
        }

        try {
            String redisKey = KEY_PREFIX + codeGroup;
            Set<String> members = stringRedisTemplate.opsForSet().members(redisKey);
            if (members == null || members.isEmpty()) {
                refreshCache(codeGroup);
                members = stringRedisTemplate.opsForSet().members(redisKey);
            }

            if (members != null) {
                Set<String> result = new HashSet<>(members);
                result.remove(EMPTY_SENTINEL);
                return Collections.unmodifiableSet(result);
            }
        } catch (Exception e) {
            log.warn("Redis cache unavailable for getValidCodes({}). Falling back to Database.", codeGroup);
        }

        List<String> dbCodes = codeListValueRepository.findActiveCodeValuesByCodeGroup(codeGroup);
        return new HashSet<>(dbCodes);
    }

    /**
     * Evicts and reloads the Redis cache for a specific code group.
     */
    public void refreshCache(String codeGroup) {
        String redisKey = KEY_PREFIX + codeGroup;
        List<String> activeValues = codeListValueRepository.findActiveCodeValuesByCodeGroup(codeGroup);

        try {
            stringRedisTemplate.delete(redisKey);
            if (!activeValues.isEmpty()) {
                stringRedisTemplate.opsForSet().add(redisKey, activeValues.toArray(new String[0]));
                stringRedisTemplate.expire(redisKey, CACHE_TTL);
            } else {
                // Prevent cache penetration with a short-lived sentinel
                stringRedisTemplate.opsForSet().add(redisKey, EMPTY_SENTINEL);
                stringRedisTemplate.expire(redisKey, Duration.ofMinutes(5));
            }
        } catch (Exception e) {
            log.error("Failed to write to Redis for group {}: {}", codeGroup, e.getMessage());
        }
    }

    /**
     * Purges cache key for a code group.
     */
    public void evictCache(String codeGroup) {
        try {
            stringRedisTemplate.delete(KEY_PREFIX + codeGroup);
            log.info("Evicted Redis cache for code group: {}", codeGroup);
        } catch (Exception e) {
            log.error("Failed to evict Redis cache for group {}: {}", codeGroup, e.getMessage());
        }
    }

    private boolean validateFromDatabase(String codeGroup, String codeValue) {
        List<String> values = codeListValueRepository.findActiveCodeValuesByCodeGroup(codeGroup);
        return values.contains(codeValue.trim());
    }
}

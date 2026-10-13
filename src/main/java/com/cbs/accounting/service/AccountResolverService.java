package com.cbs.accounting.service;

import com.cbs.accounting.repository.AccountMappingDetailRepository;
import com.cbs.common.exception.AccountMappingNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;
import java.util.Set;

/**
 * Account Abstraction Resolver Service.
 * Translates abstract accounting roles (e.g., SYS_CASH_VAULT) to concrete physical
 * GL accounts from the Chart of Accounts using a 3-tier fallback matrix cached in Redis.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountResolverService {

    public static final String CACHE_KEY_PREFIX = "cbs:acctmap:";
    private static final Duration CACHE_TTL = Duration.ofHours(2);

    private final AccountMappingDetailRepository detailRepository;
    private final StringRedisTemplate stringRedisTemplate;

    /**
     * Resolves physical GL code with full contextual coordinates:
     * Tier 1: (mappingId, branchCode, currency, productCode)
     * Tier 2: (mappingId, branchCode, currency, 'ALL')
     * Tier 3: (mappingId, 'ALL', currency, 'ALL')
     * Tier 4: (mappingId, 'ALL', 'ALL', 'ALL')
     *
     * @param mappingId   abstract role (e.g. SYS_CASH_VAULT, SYS_FEE_INCOME)
     * @param branchCode  initiating branch code (e.g. "VN001", or "ALL")
     * @param currency    transaction currency (e.g. "VND")
     * @param productCode product catalog code (e.g. "CASA_VND", or "ALL")
     * @return concrete physical GL code (e.g. "1011", "7111")
     * @throws AccountMappingNotFoundException if no mapping rule matches
     */
    public String resolvePhysicalGl(String mappingId, String branchCode, String currency, String productCode) {
        String effectiveBranch = (branchCode == null || branchCode.isBlank()) ? "ALL" : branchCode.trim();
        String effectiveCurrency = (currency == null || currency.isBlank()) ? "ALL" : currency.trim();
        String effectiveProduct = (productCode == null || productCode.isBlank()) ? "ALL" : productCode.trim();

        String cacheKey = buildCacheKey(mappingId, effectiveBranch, effectiveCurrency, effectiveProduct);

        // 1. Try Redis cache lookup
        try {
            String cachedGl = stringRedisTemplate.opsForValue().get(cacheKey);
            if (cachedGl != null && !cachedGl.isBlank()) {
                log.debug("Cache HIT for account mapping [{}] -> {}", cacheKey, cachedGl);
                return cachedGl;
            }
        } catch (Exception e) {
            log.warn("Redis unavailable for mapping lookup [{}]. Falling back to DB: {}", cacheKey, e.getMessage());
        }

        // 2. Database hierarchical fallback lookup
        List<String> matches = detailRepository.findMatchingPhysicalGlCodes(
                mappingId, effectiveBranch, effectiveCurrency, effectiveProduct
        );

        if (matches == null || matches.isEmpty()) {
            log.error("Failed to resolve physical GL for mappingId={}, branch={}, currency={}, product={}",
                    mappingId, effectiveBranch, effectiveCurrency, effectiveProduct);
            throw new AccountMappingNotFoundException(mappingId, effectiveBranch, effectiveCurrency, effectiveProduct);
        }

        String resolvedGl = matches.getFirst();
        log.info("Resolved physical GL [{}] for role [{}] (branch={}, ccy={}, prod={})",
                resolvedGl, mappingId, effectiveBranch, effectiveCurrency, effectiveProduct);

        // 3. Populate Redis cache
        try {
            stringRedisTemplate.opsForValue().set(cacheKey, resolvedGl, CACHE_TTL);
        } catch (Exception e) {
            log.warn("Failed to write resolved GL to Redis [{}]: {}", cacheKey, e.getMessage());
        }

        return resolvedGl;
    }

    /**
     * Convenience resolver using default branch ('ALL') and product ('ALL').
     */
    public String resolvePhysicalGl(String mappingId, String currency) {
        return resolvePhysicalGl(mappingId, "ALL", currency, "ALL");
    }

    /**
     * Evicts all cached entries for a given mapping ID.
     */
    public void evictCache(String mappingId) {
        try {
            String pattern = CACHE_KEY_PREFIX + mappingId + ":*";
            Set<String> keys = stringRedisTemplate.keys(pattern);
            if (keys != null && !keys.isEmpty()) {
                stringRedisTemplate.delete(keys);
                log.info("Evicted {} cache keys for account mapping role [{}]", keys.size(), mappingId);
            }
        } catch (Exception e) {
            log.error("Failed to evict Redis cache for mappingId [{}]: {}", mappingId, e.getMessage());
        }
    }

    private String buildCacheKey(String mappingId, String branch, String currency, String product) {
        return CACHE_KEY_PREFIX + mappingId + ":" + branch + ":" + currency + ":" + product;
    }
}

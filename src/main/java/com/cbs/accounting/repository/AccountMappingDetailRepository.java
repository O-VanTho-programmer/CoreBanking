package com.cbs.accounting.repository;

import com.cbs.accounting.domain.AccountMappingDetail;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountMappingDetailRepository extends JpaRepository<AccountMappingDetail, Long> {

    /**
     * Resolves matching physical GL code using 3-tier hierarchical fallback:
     * 1. Exact match: (branch, currency, product)
     * 2. Product wildcard: (branch, currency, product = 'ALL')
     * 3. Branch wildcard: (branch = 'ALL', currency, product = 'ALL')
     * 4. Global fallback: (branch = 'ALL', currency = 'ALL', product = 'ALL')
     */
    @Query("SELECT d.physicalGlCode FROM AccountMappingDetail d " +
           "WHERE d.mappingId = :mappingId " +
           "  AND d.isActive = 'Y' " +
           "  AND (d.currency = :currency OR d.currency = 'ALL') " +
           "  AND (d.branchCode = :branchCode OR d.branchCode = 'ALL') " +
           "  AND (d.productCode = :productCode OR d.productCode = 'ALL') " +
           "ORDER BY " +
           "  CASE WHEN d.branchCode = :branchCode THEN 0 ELSE 1 END, " +
           "  CASE WHEN d.productCode = :productCode THEN 0 ELSE 1 END, " +
           "  CASE WHEN d.currency = :currency THEN 0 ELSE 1 END")
    List<String> findMatchingPhysicalGlCodes(
            @Param("mappingId") String mappingId,
            @Param("branchCode") String branchCode,
            @Param("currency") String currency,
            @Param("productCode") String productCode);

    Optional<AccountMappingDetail> findFirstByMappingIdAndBranchCodeAndCurrencyAndProductCodeAndIsActive(
            String mappingId, String branchCode, String currency, String productCode, String isActive);
}

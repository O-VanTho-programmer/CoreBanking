package com.cbs.account.repository;

import com.cbs.account.domain.FdInterestRateMatrix;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface FdInterestRateMatrixRepository extends JpaRepository<FdInterestRateMatrix, Long> {

    @Query("""
        SELECT m FROM FdInterestRateMatrix m
        WHERE m.productCode = :productCode
          AND m.termMonths = :termMonths
          AND m.status = 'ACTIVE'
          AND m.effectiveFrom <= :workingDate
          AND (m.effectiveTo IS NULL OR m.effectiveTo >= :workingDate)
        ORDER BY m.effectiveFrom DESC
    """)
    List<FdInterestRateMatrix> findActiveRates(
            @Param("productCode") String productCode,
            @Param("termMonths") Integer termMonths,
            @Param("workingDate") LocalDate workingDate
    );

    default Optional<FdInterestRateMatrix> findActiveRate(String productCode, Integer termMonths, LocalDate workingDate) {
        List<FdInterestRateMatrix> list = findActiveRates(productCode, termMonths, workingDate);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    @Query("""
        SELECT m FROM FdInterestRateMatrix m
        WHERE m.productCode = :productCode
          AND m.status = 'ACTIVE'
          AND m.effectiveFrom <= :workingDate
          AND (m.effectiveTo IS NULL OR m.effectiveTo >= :workingDate)
        ORDER BY m.termMonths ASC
    """)
    List<FdInterestRateMatrix> findAllActiveTermsForProduct(
            @Param("productCode") String productCode,
            @Param("workingDate") LocalDate workingDate
    );
}

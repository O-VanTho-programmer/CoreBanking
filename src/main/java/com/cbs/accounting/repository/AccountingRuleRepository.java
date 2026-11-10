package com.cbs.depositing.repository;

import com.cbs.depositing.domain.AccountingRuleDefinition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface AccountingRuleRepository extends JpaRepository<AccountingRuleDefinition, Long> {

    @Query("SELECT r FROM AccountingRuleDefinition r " +
           "WHERE r.trxCode = :trxCode " +
           "  AND r.isActive = 'Y' " +
           "  AND :workingDate >= r.effectiveFrom AND :workingDate <= r.effectiveTo " +
           "ORDER BY r.entryGroup ASC, r.entryIndex ASC")
    List<AccountingRuleDefinition> findActiveRulesByTrxCode(
            @Param("trxCode") String trxCode,
            @Param("workingDate") LocalDate workingDate);
}

package com.cbs.eod.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * Entity mapping table ACCOUNT_BALANCE_BUCKET.
 * Stores frozen end-of-day balances, daily turnovers, and accrued interest for customer accounts.
 */
@Entity
@Table(name = "ACCOUNT_BALANCE_BUCKET")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountBalanceBucket {

    @EmbeddedId
    private AccountBalanceBucketId id;

    @Column(name = "closing_balance", nullable = false)
    private BigDecimal closingBalance;

    @Column(name = "daily_debit_sum", nullable = false)
    @Builder.Default
    private BigDecimal dailyDebitSum = BigDecimal.ZERO.setScale(4);

    @Column(name = "daily_credit_sum", nullable = false)
    @Builder.Default
    private BigDecimal dailyCreditSum = BigDecimal.ZERO.setScale(4);

    @Column(name = "accrued_interest_day", nullable = false)
    @Builder.Default
    private BigDecimal accruedInterestDay = BigDecimal.ZERO.setScale(4);

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

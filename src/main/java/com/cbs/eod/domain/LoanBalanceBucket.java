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
 * Entity mapping table LOAN_BALANCE_BUCKET.
 * Stores frozen end-of-day balances, past-due amounts, DPD, loan classification group,
 * and loss provision balances for credit accounts.
 */
@Entity
@Table(name = "LOAN_BALANCE_BUCKET")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanBalanceBucket {

    @EmbeddedId
    private LoanBalanceBucketId id;

    @Column(name = "outstanding_principal", nullable = false)
    private BigDecimal outstandingPrincipal;

    @Column(name = "past_due_principal", nullable = false)
    @Builder.Default
    private BigDecimal pastDuePrincipal = BigDecimal.ZERO;

    @Column(name = "in_term_principal", nullable = false)
    @Builder.Default
    private BigDecimal inTermPrincipal = BigDecimal.ZERO;

    @Column(name = "accrued_interest", nullable = false)
    @Builder.Default
    private BigDecimal accruedInterest = BigDecimal.ZERO;

    @Column(name = "overdue_interest", nullable = false)
    @Builder.Default
    private BigDecimal overdueInterest = BigDecimal.ZERO;

    @Column(name = "penalty_due", nullable = false)
    @Builder.Default
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "loan_group", nullable = false)
    private Integer loanGroup;

    @Column(name = "days_past_due", nullable = false)
    private Integer daysPastDue;

    @Column(name = "general_provision", nullable = false)
    @Builder.Default
    private BigDecimal generalProvision = BigDecimal.ZERO;

    @Column(name = "specific_provision", nullable = false)
    @Builder.Default
    private BigDecimal specificProvision = BigDecimal.ZERO;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

package com.cbs.deposit.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table ACCOUNT_HOLD (Funds Earmark / Hold Master).
 * Enforces constraint chk_hold_status_dates:
 * status = 'ACTIVE' <=> released_at IS NULL
 * status IN ('RELEASED', 'SETTLED') <=> released_at IS NOT NULL
 */
@Entity
@Table(name = "ACCOUNT_HOLD")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountHold {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "hold_id", nullable = false)
    private Long holdId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "hold_amount", nullable = false)
    private BigDecimal holdAmount;

    @Column(name = "reason_code", length = 30, nullable = false)
    private String reasonCode;

    @Column(name = "reference_id", length = 64)
    private String referenceId;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_working_date", nullable = false)
    private LocalDate createdWorkingDate;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "released_at")
    private OffsetDateTime releasedAt;
}

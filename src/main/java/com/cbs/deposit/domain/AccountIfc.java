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
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table ACCOUNT_IFC.
 * Stores interest and fee charge instances actually applied to an individual CASA account.
 */
@Entity
@Table(name = "ACCOUNT_IFC")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountIfc {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "account_ifc_id", nullable = false)
    private Long accountIfcId;

    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "ifc_code", length = 30, nullable = false)
    private String ifcCode;

    @Column(name = "applied_rate", nullable = false)
    private BigDecimal appliedRate;

    @Column(name = "is_waived", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isWaived = "N";

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

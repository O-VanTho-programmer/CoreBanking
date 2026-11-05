package com.cbs.lending.domain;

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
import java.time.OffsetDateTime;

/**
 * Entity mapping table LOAN_ACCOUNT_IFC.
 * Stores interest and fee charge instances actually applied to an individual loan contract.
 */
@Entity
@Table(name = "LOAN_ACCOUNT_IFC")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccountIfc {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "contract_ifc_id", nullable = false)
    private Long contractIfcId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "ifc_code", length = 30, nullable = false)
    private String ifcCode;

    @Column(name = "applied_rate", nullable = false)
    private BigDecimal appliedRate;

    @Column(name = "margin_rate", nullable = false)
    @Builder.Default
    private BigDecimal marginRate = BigDecimal.ZERO;

    @Column(name = "is_waived", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isWaived = "N";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

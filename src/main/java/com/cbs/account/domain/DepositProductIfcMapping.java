package com.cbs.account.domain;

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
 * Entity mapping table DEPOSIT_PRODUCT_IFC_MAPPING.
 * Maps multiple IFC charges to a single deposit product catalog entry (N-N relationship).
 */
@Entity
@Table(name = "DEPOSIT_PRODUCT_IFC_MAPPING")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DepositProductIfcMapping {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "mapping_id", nullable = false)
    private Long mappingId;

    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(name = "ifc_code", length = 30, nullable = false)
    private String ifcCode;

    @Column(name = "default_rate", nullable = false)
    private BigDecimal defaultRate;

    @Column(name = "min_rate", nullable = false)
    private BigDecimal minRate;

    @Column(name = "max_rate", nullable = false)
    private BigDecimal maxRate;

    @Column(name = "is_mandatory", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isMandatory = "Y";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

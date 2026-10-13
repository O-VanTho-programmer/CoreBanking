package com.cbs.accounting.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entity mapping table ACCOUNT_MAPPING_DETAIL.
 * Concrete resolver matrix that maps logical roles to physical GL codes.
 */
@Entity
@Table(name = "ACCOUNT_MAPPING_DETAIL")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountMappingDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "detail_id", nullable = false)
    private Long detailId;

    @Column(name = "mapping_id", length = 30, nullable = false)
    private String mappingId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "mapping_id", insertable = false, updatable = false)
    private AccountMapping accountMapping;

    @Column(name = "branch_code", length = 10, nullable = false)
    @Builder.Default
    private String branchCode = "ALL";

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String currency = "ALL";

    @Column(name = "product_code", length = 10, nullable = false)
    @Builder.Default
    private String productCode = "ALL";

    @Column(name = "physical_gl_code", length = 20, nullable = false)
    private String physicalGlCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "physical_gl_code", insertable = false, updatable = false)
    private GlAccount physicalGlAccount;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";
}

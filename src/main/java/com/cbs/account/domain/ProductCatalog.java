package com.cbs.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;

/**
 * Entity mapping table PRODUCT_CATALOG.
 * Master configuration template for deposit products (CASA, Term Deposits).
 */
@Entity
@Table(name = "PRODUCT_CATALOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProductCatalog {

    @Id
    @Column(name = "product_code", length = 10, nullable = false)
    private String productCode;

    @Column(name = "product_name", length = 100, nullable = false)
    private String productName;

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "default_gl_code", length = 20, nullable = false)
    private String defaultGlCode;

    @Column(name = "interest_gl_code", length = 20, nullable = false)
    private String interestGlCode;

    @Column(name = "accrual_gl_code", length = 20, nullable = false)
    private String accrualGlCode;

    @Column(name = "annual_rate", nullable = false)
    @Builder.Default
    private BigDecimal annualRate = BigDecimal.ZERO;

    @Column(name = "day_count_basis", length = 10, nullable = false)
    @Builder.Default
    private String dayCountBasis = "ACT_365";

    @Column(name = "min_balance", nullable = false)
    @Builder.Default
    private BigDecimal minBalance = BigDecimal.ZERO;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";
}

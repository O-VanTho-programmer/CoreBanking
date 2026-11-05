package com.cbs.lending.domain;

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
import java.time.OffsetDateTime;

/**
 * Entity mapping table CREDIT_PRODUCT_CATALOG.
 * Master policy configuration for lending and credit products.
 */
@Entity
@Table(name = "CREDIT_PRODUCT_CATALOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditProductCatalog {

    @Id
    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(name = "product_name", length = 150, nullable = false)
    private String productName;

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String currency = "VND";

    @Column(name = "principal_gl_code", length = 20, nullable = false)
    private String principalGlCode;

    @Column(name = "interest_revenue_gl_code", length = 20, nullable = false)
    private String interestRevenueGlCode;

    @Column(name = "interest_receivable_gl_code", length = 20, nullable = false)
    private String interestReceivableGlCode;

    @Column(name = "overdue_principal_gl_code", length = 20, nullable = false)
    private String overduePrincipalGlCode;

    @Column(name = "base_interest_rate", nullable = false)
    private BigDecimal baseInterestRate;

    @Column(name = "default_margin_rate", nullable = false)
    @Builder.Default
    private BigDecimal defaultMarginRate = BigDecimal.ZERO;

    @Column(name = "min_margin_rate", nullable = false)
    @Builder.Default
    private BigDecimal minMarginRate = BigDecimal.ZERO;

    @Column(name = "max_margin_rate", nullable = false)
    @Builder.Default
    private BigDecimal maxMarginRate = new BigDecimal("0.1000");

    @Column(name = "overdue_rate_multiplier", nullable = false)
    @Builder.Default
    private BigDecimal overdueRateMultiplier = new BigDecimal("1.50");

    @Column(name = "day_count_basis", length = 10, nullable = false)
    @Builder.Default
    private String dayCountBasis = "ACT_365";

    @Column(name = "min_principal_amount", nullable = false)
    private BigDecimal minPrincipalAmount;

    @Column(name = "max_principal_amount", nullable = false)
    private BigDecimal maxPrincipalAmount;

    @Column(name = "min_term_months", nullable = false)
    private Integer minTermMonths;

    @Column(name = "max_term_months", nullable = false)
    private Integer maxTermMonths;

    @Column(name = "default_repayment_type", length = 20, nullable = false)
    private String defaultRepaymentType;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

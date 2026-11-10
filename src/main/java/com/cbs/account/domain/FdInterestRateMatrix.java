package com.cbs.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
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
 * Entity mapping table FD_INTEREST_RATE_MATRIX.
 * Defines base annual interest rates per deposit term (in months) for Fixed Deposit products.
 */
@Entity
@Table(name = "FD_INTEREST_RATE_MATRIX")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FdInterestRateMatrix {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "base_rate", nullable = false)
    private BigDecimal baseRate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to")
    private LocalDate effectiveTo;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

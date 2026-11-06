package com.cbs.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * Entity mapping table CUSTOMER_CATALOG.
 * Customer segment definitions and KYC compliance rules.
 */
@Entity
@Table(name = "CUSTOMER_CATALOG")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CustomerCatalog {

    @Id
    @Column(name = "type_code", length = 30, nullable = false)
    private String typeCode;

    @Column(name = "type_name", length = 100, nullable = false)
    private String typeName;

    @Column(name = "segment", length = 50, nullable = false)
    private String segment;

    @Column(name = "requires_tax_id", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @Builder.Default
    private String requiresTaxId = "N";

    @Column(name = "min_age", nullable = false)
    @Builder.Default
    private Integer minAge = 18;

    @Column(name = "default_kyc_level", length = 30, nullable = false)
    @Builder.Default
    private String defaultKycLevel = "STANDARD";

    @Column(name = "display_order", nullable = false)
    @Builder.Default
    private Integer displayOrder = 1;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @Builder.Default
    private String isActive = "Y";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

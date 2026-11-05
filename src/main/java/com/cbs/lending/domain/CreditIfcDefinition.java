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

import java.time.OffsetDateTime;

/**
 * Entity mapping table CREDIT_IFC_DEFINITION.
 * Master catalog defining all Interest and Fee Charge (IFC) types for credit and lending.
 */
@Entity
@Table(name = "CREDIT_IFC_DEFINITION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreditIfcDefinition {

    @Id
    @Column(name = "ifc_code", length = 30, nullable = false)
    private String ifcCode;

    @Column(name = "ifc_name", length = 100, nullable = false)
    private String ifcName;

    @Column(name = "ifc_type", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String ifcType; // 'I' = Interest (Revenue), 'F' = Fee (Income)

    @Column(name = "calculation_method", length = 20, nullable = false)
    private String calculationMethod; // PERCENTAGE, FLAT, SLAB

    @Column(name = "frequency", length = 20, nullable = false)
    private String frequency; // DAILY, MONTHLY, ONETIME

    @Column(name = "gl_income_mapping", length = 50)
    private String glIncomeMapping;

    @Column(name = "gl_receivable_mapping", length = 50)
    private String glReceivableMapping;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

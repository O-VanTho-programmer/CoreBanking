package com.cbs.common.codelist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Entity mapping table CODELIST_VALUE.
 * Stores individual code items within a group (e.g. ACTIVE, BLOCKED).
 */
@Entity
@Table(name = "CODELIST_VALUE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeListValue {

    @EmbeddedId
    private CodeListValueId id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "code_group", insertable = false, updatable = false)
    private CodeList codeList;

    @Column(name = "display_name_vi", length = 100, nullable = false)
    private String displayNameVi;

    @Column(name = "display_name_en", length = 100, nullable = false)
    private String displayNameEn;

    @Column(name = "sort_order", nullable = false)
    @Builder.Default
    private Integer sortOrder = 1;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";
}

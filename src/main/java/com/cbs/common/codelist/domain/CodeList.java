package com.cbs.common.codelist.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entity mapping table CODELIST.
 * Stores master definitions of code categories (e.g. ACCT_STATUS, KYC_STATUS).
 */
@Entity
@Table(name = "CODELIST")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CodeList {

    @Id
    @Column(name = "code_group", length = 30, nullable = false)
    private String codeGroup;

    @Column(name = "code_group_name", length = 100, nullable = false)
    private String codeGroupName;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "is_system", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.CHAR)
    @Builder.Default
    private String isSystem = "Y";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @OneToMany(mappedBy = "codeList", cascade = CascadeType.ALL, fetch = FetchType.LAZY)
    @Builder.Default
    private List<CodeListValue> values = new ArrayList<>();
}

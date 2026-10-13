package com.cbs.accounting.domain;

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
 * Entity mapping table GL_ACCOUNT (Chart of Accounts / COA).
 */
@Entity
@Table(name = "GL_ACCOUNT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GlAccount {

    @Id
    @Column(name = "gl_code", length = 20, nullable = false)
    private String glCode;

    @Column(name = "gl_name", length = 100, nullable = false)
    private String glName;

    @Column(name = "account_type", length = 15, nullable = false)
    private String accountType;

    @Column(name = "normal_balance", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String normalBalance;

    @Column(name = "parent_gl_code", length = 20)
    private String parentGlCode;

    @Column(name = "gl_level", nullable = false)
    @Builder.Default
    private Integer glLevel = 1;

    @Column(name = "is_postable", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isPostable = "N";

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

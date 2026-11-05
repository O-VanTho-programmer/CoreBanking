package com.cbs.form.domain;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.OffsetDateTime;

@Entity
@Table(name = "FORM")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormEntity {

    @Id
    @Column(name = "form_code", length = 50, nullable = false)
    private String formCode;

    @Column(name = "trx_code", length = 20, nullable = false)
    private String trxCode;

    @Column(name = "module", length = 20, nullable = false)
    private String module;

    @Column(name = "form_title", length = 150, nullable = false)
    private String formTitle;

    @Column(name = "version", nullable = false)
    @Builder.Default
    private Integer version = 1;

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";

    @Lob
    @Column(name = "schema_payload", nullable = false)
    private String schemaPayload;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

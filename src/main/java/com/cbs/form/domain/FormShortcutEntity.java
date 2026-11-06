package com.cbs.form.domain;

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

import java.time.Instant;

@Entity
@Table(name = "FORM_SHORTCUT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FormShortcutEntity {

    @Id
    @Column(name = "shortcut_code", length = 50, nullable = false)
    private String shortcutCode;

    @Column(name = "form_code", length = 50, nullable = false)
    private String formCode;

    @Column(name = "form_name", length = 150, nullable = false)
    private String formName;

    @Column(name = "category", length = 50, nullable = false)
    private String category;

    @Column(name = "module", length = 20, nullable = false)
    private String module;

    @Column(name = "keywords", length = 255, nullable = false)
    private String keywords;

    @Column(name = "icon_name", length = 50, nullable = false)
    private String iconName;

    @Column(name = "display_order", nullable = false)
    private Integer displayOrder;

    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "is_active", columnDefinition = "CHAR(1)", nullable = false)
    private String isActive;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;
}

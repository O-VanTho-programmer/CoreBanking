package com.cbs.common.codelist.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;

/**
 * Composite Primary Key for CODELIST_VALUE table.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class CodeListValueId implements Serializable {

    @Column(name = "code_group", length = 30, nullable = false)
    private String codeGroup;

    @Column(name = "code_value", length = 30, nullable = false)
    private String codeValue;
}

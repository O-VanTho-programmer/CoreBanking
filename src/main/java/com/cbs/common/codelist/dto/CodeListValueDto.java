package com.cbs.common.codelist.dto;

public record CodeListValueDto(
        String codeGroup,
        String codeValue,
        String displayNameVi,
        String displayNameEn,
        Integer sortOrder
) {}

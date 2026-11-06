package com.cbs.form.dto;

import com.cbs.form.domain.FormShortcutEntity;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FormShortcutResponse {
    private String shortcutCode;
    private String formCode;
    private String formName;
    private String category;
    private String module;
    private String keywords;
    private String iconName;
    private Integer displayOrder;

    public static FormShortcutResponse fromEntity(FormShortcutEntity entity) {
        return FormShortcutResponse.builder()
                .shortcutCode(entity.getShortcutCode())
                .formCode(entity.getFormCode())
                .formName(entity.getFormName())
                .category(entity.getCategory())
                .module(entity.getModule())
                .keywords(entity.getKeywords())
                .iconName(entity.getIconName())
                .displayOrder(entity.getDisplayOrder())
                .build();
    }
}

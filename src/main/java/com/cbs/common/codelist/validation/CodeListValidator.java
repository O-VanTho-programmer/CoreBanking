package com.cbs.common.codelist.validation;

import com.cbs.common.codelist.service.CodeListService;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Validator implementation for @ValidCodeList.
 * Validates candidate string values against the active codes cached in Redis.
 */
@Component
@RequiredArgsConstructor
public class CodeListValidator implements ConstraintValidator<ValidCodeList, String> {

    private final CodeListService codeListService;
    private String codeGroup;

    @Override
    public void initialize(ValidCodeList constraintAnnotation) {
        this.codeGroup = constraintAnnotation.group();
    }

    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        // Standard Bean Validation convention: null or blank is considered valid.
        // Use @NotNull or @NotBlank in combination if the field is mandatory.
        if (value == null || value.isBlank()) {
            return true;
        }

        return codeListService.isValid(codeGroup, value);
    }
}

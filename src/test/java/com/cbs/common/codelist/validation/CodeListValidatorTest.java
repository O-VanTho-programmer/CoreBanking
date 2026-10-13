package com.cbs.common.codelist.validation;

import com.cbs.common.codelist.service.CodeListService;
import jakarta.validation.ConstraintValidatorContext;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CodeListValidatorTest {

    @Mock
    private CodeListService codeListService;

    @Mock
    private ConstraintValidatorContext context;

    @InjectMocks
    private CodeListValidator validator;

    @BeforeEach
    void setUp() {
        ValidCodeList annotation = mock(ValidCodeList.class);
        when(annotation.group()).thenReturn("ACCT_STATUS");
        validator.initialize(annotation);
    }

    @Test
    @DisplayName("isValid() returns true for null or blank value (delegates mandatory check to @NotNull/@NotBlank)")
    void shouldReturnTrueForNullOrBlank() {
        assertThat(validator.isValid(null, context)).isTrue();
        assertThat(validator.isValid("", context)).isTrue();
        assertThat(validator.isValid("   ", context)).isTrue();

        verifyNoInteractions(codeListService);
    }

    @Test
    @DisplayName("isValid() returns true when CodeListService confirms value is valid")
    void shouldReturnTrueWhenServiceConfirmsValid() {
        when(codeListService.isValid("ACCT_STATUS", "ACTIVE")).thenReturn(true);

        assertThat(validator.isValid("ACTIVE", context)).isTrue();
    }

    @Test
    @DisplayName("isValid() returns false when CodeListService confirms value is invalid")
    void shouldReturnFalseWhenServiceConfirmsInvalid() {
        when(codeListService.isValid("ACCT_STATUS", "UNKNOWN_STATUS")).thenReturn(false);

        assertThat(validator.isValid("UNKNOWN_STATUS", context)).isFalse();
    }
}

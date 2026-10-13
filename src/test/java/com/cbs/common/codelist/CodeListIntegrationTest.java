package com.cbs.common.codelist;

import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class CodeListIntegrationTest {

    @Autowired
    private CodeListService codeListService;

    @Autowired
    private Validator validator;

    // Test record simulating a banking DTO
    record TestAccountDto(
            @ValidCodeList(group = "ACCT_STATUS")
            String accountStatus,

            @ValidCodeList(group = "KYC_STATUS")
            String kycStatus
    ) {}

    @Test
    @DisplayName("CodeListService validates seeded values from Oracle V2 migration correctly via Redis")
    void shouldValidateSeededValuesCorrectly() {
        // From V2 migration: ACCT_STATUS has ACTIVE, DORMANT, BLOCKED, CLOSED
        assertThat(codeListService.isValid("ACCT_STATUS", "ACTIVE")).isTrue();
        assertThat(codeListService.isValid("ACCT_STATUS", "DORMANT")).isTrue();
        assertThat(codeListService.isValid("ACCT_STATUS", "BLOCKED")).isTrue();
        assertThat(codeListService.isValid("ACCT_STATUS", "CLOSED")).isTrue();
        assertThat(codeListService.isValid("ACCT_STATUS", "NON_EXISTENT_STATUS")).isFalse();

        // KYC_STATUS has PENDING, VERIFIED, REJECTED
        assertThat(codeListService.isValid("KYC_STATUS", "VERIFIED")).isTrue();
        assertThat(codeListService.isValid("KYC_STATUS", "PENDING")).isTrue();
        assertThat(codeListService.isValid("KYC_STATUS", "INVALID")).isFalse();
    }

    @Test
    @DisplayName("Bean Validation @ValidCodeList passes for valid DTO and fails for invalid status")
    void shouldValidateDtoWithAnnotation() {
        // Valid DTO
        TestAccountDto validDto = new TestAccountDto("ACTIVE", "VERIFIED");
        Set<ConstraintViolation<TestAccountDto>> violations = validator.validate(validDto);
        assertThat(violations).isEmpty();

        // Invalid DTO
        TestAccountDto invalidDto = new TestAccountDto("HACKED_STATUS", "VERIFIED");
        Set<ConstraintViolation<TestAccountDto>> invalidViolations = validator.validate(invalidDto);
        assertThat(invalidViolations).hasSize(1);
        assertThat(invalidViolations.iterator().next().getMessage())
                .contains("ACCT_STATUS");
    }
}

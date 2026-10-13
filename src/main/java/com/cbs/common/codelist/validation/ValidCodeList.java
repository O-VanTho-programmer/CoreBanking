package com.cbs.common.codelist.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Jakarta Bean Validation annotation for validating status/type fields
 * against dynamic CodeList definitions in Redis.
 *
 * Example:
 * <pre>
 *   public record CreateCustomerCommand(
 *       @NotBlank String cifNumber,
 *       @ValidCodeList(group = "CUST_STATUS") String status,
 *       @ValidCodeList(group = "KYC_STATUS") String kycStatus
 *   ) {}
 * </pre>
 */
@Documented
@Constraint(validatedBy = CodeListValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER, ElementType.RECORD_COMPONENT})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidCodeList {

    /**
     * The code group to validate against (e.g., "ACCT_STATUS", "CUST_STATUS", "KYC_STATUS").
     */
    String group();

    /**
     * Validation error message template.
     */
    String message() default "Invalid code value for group '{group}'";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}

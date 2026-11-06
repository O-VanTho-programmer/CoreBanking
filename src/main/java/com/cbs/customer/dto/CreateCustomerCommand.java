package com.cbs.customer.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Command for registering a new customer (CIF).
 */
@Builder
public record CreateCustomerCommand(
        String customerType,

        @NotBlank(message = "fullName is required")
        String fullName,

        String gender,

        @NotBlank(message = "nationalId is required")
        String nationalId,

        String phoneNumber,

        String emailAddress,

        String taxNumber,

        String streetAddress,

        String commune,

        String district,

        String province,

        String country,

        @ValidCodeList(group = "KYC_STATUS")
        String kycStatus,

        @ValidCodeList(group = "CUST_STATUS")
        String status
) {
    public CreateCustomerCommand {
        if (customerType == null || customerType.isBlank()) {
            customerType = "INDIVIDUAL";
        }
        if (gender == null || gender.isBlank()) {
            gender = "OTHER";
        }
        if (country == null || country.isBlank()) {
            country = "VN";
        }
        if (kycStatus == null || kycStatus.isBlank()) {
            kycStatus = "PENDING";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }
}

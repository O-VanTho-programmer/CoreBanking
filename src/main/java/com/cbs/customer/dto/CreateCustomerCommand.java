package com.cbs.customer.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import lombok.Builder;

/**
 * Command for registering a new customer (CIF).
 */
@Builder
public record CreateCustomerCommand(
        @ValidCodeList(group = "CUST_TYPE")
        String customerType,

        @NotBlank(message = "fullName is required")
        String fullName,

        @NotBlank(message = "nationalId is required")
        String nationalId,

        String taxNumber,

        @ValidCodeList(group = "KYC_STATUS")
        String kycStatus,

        @ValidCodeList(group = "CUST_STATUS")
        String status
) {
    public CreateCustomerCommand {
        if (customerType == null || customerType.isBlank()) {
            customerType = "INDIVIDUAL";
        }
        if (kycStatus == null || kycStatus.isBlank()) {
            kycStatus = "PENDING";
        }
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }
}

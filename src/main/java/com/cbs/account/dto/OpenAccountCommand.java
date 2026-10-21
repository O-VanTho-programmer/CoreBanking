package com.cbs.account.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

/**
 * Command for opening a new customer CASA account.
 */
@Builder
public record OpenAccountCommand(
        @NotNull(message = "customerId is mandatory")
        Long customerId,

        @NotBlank(message = "productCode is mandatory")
        String productCode,

        @NotBlank(message = "branchCode is mandatory")
        String branchCode,

        @ValidCodeList(group = "ACCT_STATUS")
        String status
) {
    public OpenAccountCommand {
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
    }
}

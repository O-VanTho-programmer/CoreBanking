package com.cbs.account.dto;

import com.cbs.common.codelist.validation.ValidCodeList;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;

import java.math.BigDecimal;

/**
 * Command for opening a new customer deposit account (CASA or Fixed Deposit).
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
        String status,

        BigDecimal initialDeposit,
        Integer termMonths,

        @ValidCodeList(group = "ROLLOVER_OPTION")
        String rolloverOption,

        Boolean requestInterestMargin,

        @ValidCodeList(group = "MARGIN_REASON")
        String marginReason,

        BigDecimal interestMargin,
        String linkedCasaAccountNumber
) {
    public OpenAccountCommand {
        if (status == null || status.isBlank()) {
            status = "ACTIVE";
        }
        if (initialDeposit == null) {
            initialDeposit = BigDecimal.ZERO;
        }
        if (interestMargin == null) {
            interestMargin = BigDecimal.ZERO;
        }
    }
}

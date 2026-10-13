package com.cbs.common.exception;

/**
 * Thrown when the Account Abstraction Resolver cannot map a logical account role
 * (e.g. SYS_CASH_VAULT) to a concrete physical GL code in the Chart of Accounts.
 */
public class AccountMappingNotFoundException extends RuntimeException {

    private final String mappingId;
    private final String branchCode;
    private final String currency;
    private final String productCode;

    public AccountMappingNotFoundException(String mappingId, String branchCode, String currency, String productCode) {
        super(String.format("No active physical GL mapping found for role '%s' [branch='%s', currency='%s', product='%s']",
                mappingId, branchCode, currency, productCode));
        this.mappingId = mappingId;
        this.branchCode = branchCode;
        this.currency = currency;
        this.productCode = productCode;
    }

    public String getMappingId() {
        return mappingId;
    }

    public String getBranchCode() {
        return branchCode;
    }

    public String getCurrency() {
        return currency;
    }

    public String getProductCode() {
        return productCode;
    }
}

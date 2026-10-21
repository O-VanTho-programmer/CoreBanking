package com.cbs.common.exception;

import java.math.BigDecimal;

/**
 * Thrown when an account has insufficient available balance (Invariant 2: Available = Ledger - Hold).
 */
public class InsufficientFundsException extends RuntimeException {

    private final Long accountId;
    private final BigDecimal availableBalance;
    private final BigDecimal requestedAmount;

    public InsufficientFundsException(Long accountId, BigDecimal availableBalance, BigDecimal requestedAmount) {
        super(String.format("Insufficient funds on accountId=%d: AvailableBalance=%s, RequestedAmount=%s",
                accountId, availableBalance, requestedAmount));
        this.accountId = accountId;
        this.availableBalance = availableBalance;
        this.requestedAmount = requestedAmount;
    }

    public Long getAccountId() {
        return accountId;
    }

    public BigDecimal getAvailableBalance() {
        return availableBalance;
    }

    public BigDecimal getRequestedAmount() {
        return requestedAmount;
    }
}

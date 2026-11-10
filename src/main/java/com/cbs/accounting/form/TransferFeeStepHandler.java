package com.cbs.depositing.form;

import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class TransferFeeStepHandler implements FormStepHandler {

    @Override
    public String getStepCode() {
        return "CALCULATE_TRANSFER_FEE";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        BigDecimal amount = context.getBigDecimal("amount");
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            context.putUpdate("feeAmount", BigDecimal.ZERO);
            return;
        }

        // Standard tier fee: > 10,000,000 VND -> 11,000 VND, else 5,500 VND
        BigDecimal fee = (amount.compareTo(new BigDecimal("10000000")) > 0)
                ? new BigDecimal("11000.0000")
                : new BigDecimal("5500.0000");

        context.putUpdate("feeAmount", fee);

        // Pre-validate Invariant 2 if available balance is known
        BigDecimal available = context.getBigDecimal("availableBalance");
        if (available != null) {
            BigDecimal totalDebit = amount.add(fee);
            if (available.compareTo(totalDebit) < 0) {
                context.putError("amount", String.format(
                        "Available balance (%s) is insufficient to transfer %s (plus fee %s)!",
                        available, amount, fee));
            }
        }
    }
}

package com.cbs.deposit.form;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import com.cbs.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class DestAccountLookupStepHandler implements FormStepHandler {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    @Override
    public String getStepCode() {
        return "LOOKUP_DEST_ACCOUNT";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String accNum = context.getString("toAccountNumber");
        if (accNum == null || accNum.isBlank()) {
            return;
        }

        String fromNum = context.getString("fromAccountNumber");
        if (accNum.equals(fromNum)) {
            context.putError("toAccountNumber", "Beneficiary account cannot be the same as source account!");
            return;
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accNum);
        if (accOpt.isEmpty()) {
            context.putError("toAccountNumber", "Beneficiary account does not exist!");
            context.putUpdate("destAccountName", "");
            return;
        }

        Account acc = accOpt.get();
        String customerName = customerRepository.findById(acc.getCustomerId())
                .map(c -> c.getFullName())
                .orElse("CUST-" + acc.getCustomerId());

        context.putUpdate("destAccountName", customerName);

        if (!"ACTIVE".equals(acc.getStatus())) {
            context.putError("toAccountNumber", "Beneficiary account is currently " + acc.getStatus() + "!");
        }
    }
}

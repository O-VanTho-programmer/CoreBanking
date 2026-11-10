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
public class SourceAccountLookupStepHandler implements FormStepHandler {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    @Override
    public String getStepCode() {
        return "LOOKUP_SOURCE_ACCOUNT";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String accNum = context.getString("fromAccountNumber");
        if (accNum == null || accNum.isBlank()) {
            return;
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accNum);
        if (accOpt.isEmpty()) {
            context.putError("fromAccountNumber", "Source account does not exist!");
            context.putUpdate("sourceAccountName", "");
            context.putUpdate("availableBalance", null);
            return;
        }

        Account acc = accOpt.get();
        String customerName = customerRepository.findById(acc.getCustomerId())
                .map(c -> c.getFullName())
                .orElse("CUST-" + acc.getCustomerId());

        context.putUpdate("sourceAccountName", customerName);
        context.putUpdate("availableBalance", acc.getAvailableBalance());
        context.putUpdate("currency", acc.getCurrency());

        if (!"ACTIVE".equals(acc.getStatus())) {
            context.putError("fromAccountNumber", "Source account status is " + acc.getStatus() + "!");
        }
    }
}

package com.cbs.account.form;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
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
            context.putError("toAccountNumber", "Tài khoản thụ hưởng không được trùng với tài khoản nguồn!");
            return;
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accNum);
        if (accOpt.isEmpty()) {
            context.putError("toAccountNumber", "Tài khoản thụ hưởng không tồn tại!");
            context.putUpdate("destAccountName", "");
            return;
        }

        Account acc = accOpt.get();
        String customerName = customerRepository.findById(acc.getCustomerId())
                .map(c -> c.getFullName())
                .orElse("KH-" + acc.getCustomerId());

        context.putUpdate("destAccountName", customerName);

        if (!"ACTIVE".equals(acc.getStatus())) {
            context.putError("toAccountNumber", "Tài khoản thụ hưởng đang bị " + acc.getStatus() + "!");
        }
    }
}

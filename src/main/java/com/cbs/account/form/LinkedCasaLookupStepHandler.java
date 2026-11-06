package com.cbs.account.form;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LinkedCasaLookupStepHandler implements FormStepHandler {

    private final AccountRepository accountRepository;

    @Override
    public String getStepCode() {
        return "LOOKUP_LINKED_CASA";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String accNum = context.getString("linkedCasaAccountNumber");
        if (accNum == null || accNum.isBlank()) {
            return;
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accNum);
        if (accOpt.isEmpty()) {
            context.putError("linkedCasaAccountNumber", "Linked CASA account does not exist!");
            return;
        }

        Account acc = accOpt.get();
        if (!"ACTIVE".equals(acc.getStatus())) {
            context.putError("linkedCasaAccountNumber", "Linked CASA account status is " + acc.getStatus() + ", cannot bind for disbursement!");
            return;
        }

        // Validate that CASA belongs to the selected customer if cifNumber is already resolved
        Long customerId = context.getInteger("customerId") != null ? context.getInteger("customerId").longValue() : null;
        if (customerId != null && !customerId.equals(acc.getCustomerId())) {
            context.putError("linkedCasaAccountNumber", "Linked CASA account does not belong to the selected customer CIF!");
        }
    }
}

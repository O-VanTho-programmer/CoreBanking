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
public class AccountLookupStepHandler implements FormStepHandler {

    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    @Override
    public String getStepCode() {
        return "LOOKUP_ACCOUNT";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String accNum = context.getString("accountNumber");
        if (accNum == null || accNum.isBlank()) {
            return;
        }

        Optional<Account> accOpt = accountRepository.findByAccountNumber(accNum);
        if (accOpt.isEmpty()) {
            context.putError("accountNumber", "Số tài khoản không tồn tại trên hệ thống!");
            context.putUpdate("accountHolderName", "");
            context.putUpdate("availableBalance", null);
            return;
        }

        Account acc = accOpt.get();
        String customerName = customerRepository.findById(acc.getCustomerId())
                .map(c -> c.getFullName())
                .orElse("KH-" + acc.getCustomerId());

        context.putUpdate("accountHolderName", customerName);
        context.putUpdate("availableBalance", acc.getAvailableBalance());
        context.putUpdate("currency", acc.getCurrency());
        context.putUpdate("accountStatus", acc.getStatus());

        if (!"ACTIVE".equals(acc.getStatus())) {
            context.putError("accountNumber", "Tài khoản đang ở trạng thái " + acc.getStatus() + ", không thể giao dịch!");
        }
    }
}

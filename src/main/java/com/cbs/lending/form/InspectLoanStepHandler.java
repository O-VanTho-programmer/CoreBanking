package com.cbs.lending.form;

import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.repository.LoanAccountRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class InspectLoanStepHandler implements FormStepHandler {

    private final LoanAccountRepository loanAccountRepository;
    private final CustomerRepository customerRepository;

    @Override
    public String getStepCode() {
        return "INSPECT_LOAN";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String loanIdStr = context.getString("loanId");
        if (loanIdStr == null || loanIdStr.isBlank()) {
            return;
        }

        try {
            Long loanId = Long.parseLong(loanIdStr.trim());
            Optional<LoanAccount> loanOpt = loanAccountRepository.findById(loanId);
            if (loanOpt.isEmpty()) {
                context.putError("loanId", "Loan contract ID " + loanId + " does not exist!");
                context.putUpdate("customerName", "");
                context.putUpdate("principalAmount", null);
                context.putUpdate("outstandingPrincipal", null);
                return;
            }

            LoanAccount loan = loanOpt.get();
            String custName = customerRepository.findById(loan.getCustomerId())
                    .map(c -> c.getFullName())
                    .orElse("CUST-" + loan.getCustomerId());

            context.putUpdate("customerName", custName);
            context.putUpdate("principalAmount", loan.getPrincipalAmount());
            context.putUpdate("outstandingPrincipal", loan.getRemainingPrincipal());
        } catch (NumberFormatException e) {
            context.putError("loanId", "Loan ID must be a numeric identifier!");
        }
    }
}

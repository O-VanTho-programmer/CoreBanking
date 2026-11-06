package com.cbs.customer.form;

import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Component
@RequiredArgsConstructor
public class CustomerLookupStepHandler implements FormStepHandler {

    private final CustomerRepository customerRepository;

    @Override
    public String getStepCode() {
        return "LOOKUP_CUSTOMER";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String cif = context.getString("cifNumber");
        if (cif == null || cif.isBlank()) {
            return;
        }

        Optional<Customer> custOpt = customerRepository.findByCifNumber(cif);
        if (custOpt.isEmpty()) {
            context.putError("cifNumber", "CIF number does not exist in the system!");
            context.putUpdate("customerName", "");
            context.putUpdate("customerId", null);
            return;
        }

        Customer cust = custOpt.get();
        context.putUpdate("customerId", cust.getCustomerId());
        context.putUpdate("customerName", cust.getFullName());
        context.putUpdate("nationalId", cust.getNationalId());
    }
}

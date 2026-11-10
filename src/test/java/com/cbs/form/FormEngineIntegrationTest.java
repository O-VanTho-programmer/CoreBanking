package com.cbs.form;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.service.AccountService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.form.controller.FormApiController;
import com.cbs.form.dto.FormEvaluateRequest;
import com.cbs.form.dto.FormEvaluateResponse;
import com.cbs.form.dto.FormSummaryResponse;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FormEngineIntegrationTest {

    @Autowired
    private FormApiController formApiController;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Test
    @DisplayName("Form Engine: List all active forms seeded from V12 migration")
    void testListActiveForms() {
        ResponseEntity<List<FormSummaryResponse>> response = formApiController.listForms(null);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();

        List<FormSummaryResponse> forms = response.getBody();
        assertThat(forms).isNotNull();
        assertThat(forms).extracting(FormSummaryResponse::formCode)
                .contains("FORM_CASH_DEP", "FORM_CASH_WDR", "FORM_TRF_INTERNAL", "FORM_LOAN_ORIGINATION");
    }

    @Test
    @DisplayName("Form Engine: Fetch schema payload for FORM_TRF_INTERNAL")
    void testGetFormSchema() {
        ResponseEntity<String> response = formApiController.getFormSchema("FORM_TRF_INTERNAL");
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getBody()).contains("Internal Fund Transfer").contains("fromAccountNumber");
    }

    @Test
    @DisplayName("Form Engine: List shortcuts and search by keyword")
    void testListAndSearchShortcuts() {
        var allShortcuts = formApiController.listShortcuts(null);
        assertThat(allShortcuts.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(allShortcuts.getBody()).isNotEmpty();

        var searchResults = formApiController.searchShortcuts("cash");
        assertThat(searchResults.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(searchResults.getBody()).isNotEmpty();
        assertThat(searchResults.getBody().stream().anyMatch(s -> s.getFormCode().equals("FORM_CASH_DEP"))).isTrue();
    }

    @Test
    @DisplayName("Form Engine: Server-driven evaluate for account lookup and transfer fee calculation")
    void testEvaluateFormSteps() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customer and CASA Account
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Form Test User " + suffix)
                .nationalId("ID-FORM-" + suffix)
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account account = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("dep-form-" + suffix)
                .accountNumber(account.getAccountNumber())
                .amount(new BigDecimal("15000000.0000"))
                .branchCode("VN001")
                .makerId("admin")
                .build());

        // 2. Evaluate step LOOKUP_SOURCE_ACCOUNT
        Map<String, Object> formData = new HashMap<>();
        formData.put("fromAccountNumber", account.getAccountNumber());

        FormEvaluateRequest reqLookup = new FormEvaluateRequest(
                "FORM_TRF_INTERNAL",
                "fromAccountNumber",
                List.of("LOOKUP_SOURCE_ACCOUNT"),
                formData);

        ResponseEntity<FormEvaluateResponse> respLookup = formApiController.evaluateForm(reqLookup);
        assertThat(respLookup.getStatusCode().is2xxSuccessful()).isTrue();
        FormEvaluateResponse eval1 = respLookup.getBody();
        assertThat(eval1).isNotNull();
        assertThat(eval1.errors()).isEmpty();
        assertThat(eval1.updates().get("sourceAccountName")).isEqualTo("Form Test User " + suffix);
        assertThat(eval1.updates().get("availableBalance")).isNotNull();

        // 3. Evaluate step CALCULATE_TRANSFER_FEE with amount 20,000,000 (> 10M -> 11,000 VND fee)
        formData.putAll(eval1.updates());
        formData.put("amount", new BigDecimal("20000000.0000"));

        FormEvaluateRequest reqFee = new FormEvaluateRequest(
                "FORM_TRF_INTERNAL",
                "amount",
                List.of("CALCULATE_TRANSFER_FEE"),
                formData);

        ResponseEntity<FormEvaluateResponse> respFee = formApiController.evaluateForm(reqFee);
        assertThat(respFee.getStatusCode().is2xxSuccessful()).isTrue();
        FormEvaluateResponse eval2 = respFee.getBody();
        assertThat(eval2).isNotNull();
        assertThat(new BigDecimal(eval2.updates().get("feeAmount").toString())).isEqualByComparingTo("11000.0000");
    }
}

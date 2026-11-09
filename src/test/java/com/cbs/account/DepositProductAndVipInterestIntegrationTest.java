package com.cbs.account;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.service.AccountService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.form.controller.FormApiController;
import com.cbs.form.dto.FormEvaluateRequest;
import com.cbs.form.dto.FormEvaluateResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;

import java.math.BigDecimal;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DepositProductAndVipInterestIntegrationTest {

    @Autowired
    private FormApiController formApiController;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Test
    @DisplayName("Step EVALUATE_DEPOSIT_PRODUCT for CASA disables FD-specific fields")
    void testEvaluateCasaProduct() {
        Map<String, Object> formData = new HashMap<>();
        formData.put("productCode", "CASA_VND");

        FormEvaluateRequest request = new FormEvaluateRequest(
                "FORM_ACCOUNT_OPEN",
                "productCode",
                List.of("EVALUATE_DEPOSIT_PRODUCT"),
                formData
        );

        ResponseEntity<FormEvaluateResponse> response = formApiController.evaluateForm(request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        FormEvaluateResponse eval = response.getBody();
        assertThat(eval).isNotNull();

        assertThat(eval.updates().get("productType")).isEqualTo("CASA");
        assertThat(eval.disabledFields()).contains(
                "initialDeposit",
                "termMonths",
                "rolloverOption",
                "requestInterestMargin",
                "marginReason",
                "interestMargin"
        );
    }

    @Test
    @DisplayName("Step EVALUATE_DEPOSIT_PRODUCT for FD conditionally enables VIP margin fields")
    void testEvaluateFdProductWithVipMargin() {
        // 1. FD selected, but requestInterestMargin is false -> marginReason & interestMargin disabled
        Map<String, Object> formData = new HashMap<>();
        formData.put("productCode", "SAV_TERM");
        formData.put("requestInterestMargin", false);

        FormEvaluateRequest req1 = new FormEvaluateRequest(
                "FORM_ACCOUNT_OPEN",
                "productCode",
                List.of("EVALUATE_DEPOSIT_PRODUCT"),
                formData
        );
        FormEvaluateResponse eval1 = formApiController.evaluateForm(req1).getBody();
        assertThat(eval1).isNotNull();
        assertThat(eval1.updates().get("productType")).isEqualTo("FD");
        assertThat(eval1.disabledFields()).contains("marginReason", "interestMargin");

        // 2. requestInterestMargin checked, but reason not selected -> interestMargin still disabled
        formData.put("requestInterestMargin", true);
        formData.put("marginReason", "");
        FormEvaluateRequest req2 = new FormEvaluateRequest(
                "FORM_ACCOUNT_OPEN",
                "requestInterestMargin",
                List.of("EVALUATE_DEPOSIT_PRODUCT"),
                formData
        );
        FormEvaluateResponse eval2 = formApiController.evaluateForm(req2).getBody();
        assertThat(eval2).isNotNull();
        assertThat(eval2.disabledFields()).contains("interestMargin");
        assertThat(eval2.disabledFields()).doesNotContain("marginReason");

        // 3. reason selected (VIP_CUSTOMER) -> interestMargin unlocked and effective rate updated!
        formData.put("marginReason", "VIP_CUSTOMER");
        formData.put("interestMargin", new BigDecimal("0.0050"));
        FormEvaluateRequest req3 = new FormEvaluateRequest(
                "FORM_ACCOUNT_OPEN",
                "interestMargin",
                List.of("EVALUATE_DEPOSIT_PRODUCT"),
                formData
        );
        FormEvaluateResponse eval3 = formApiController.evaluateForm(req3).getBody();
        assertThat(eval3).isNotNull();
        assertThat(eval3.disabledFields()).doesNotContain("interestMargin");
        BigDecimal effectiveRate = new BigDecimal(eval3.updates().get("effectiveRate").toString());
        // Base rate 5.5% + margin 0.5% = 6.0% (0.0600)
        assertThat(effectiveRate).isEqualByComparingTo("0.0600");
    }

    @Test
    @DisplayName("Open Fixed Deposit account successfully with initial placement and VIP margin")
    void testOpenFixedDepositAccountSuccess() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .fullName("VIP Investor " + suffix)
                .nationalId("ID-VIP-" + suffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .build());

        OpenAccountCommand cmd = OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .branchCode("VN001")
                .status("ACTIVE")
                .initialDeposit(new BigDecimal("20000000.0000")) // 20M >= min_open 10M
                .termMonths(12)
                .rolloverOption("PRINCIPAL_AND_INTEREST")
                .requestInterestMargin(true)
                .marginReason("VIP_CUSTOMER")
                .interestMargin(new BigDecimal("0.0050"))
                .build();

        Account account = accountService.openAccount(cmd);
        assertThat(account).isNotNull();
        assertThat(account.getProductType()).isEqualTo("FD");
        assertThat(account.getLedgerBalance()).isEqualByComparingTo("20000000.0000");
        assertThat(account.getBaseInterestRate()).isEqualByComparingTo("0.0550");
        assertThat(account.getInterestMargin()).isEqualByComparingTo("0.0050");
        assertThat(account.getEffectiveRate()).isEqualByComparingTo("0.0600");
        assertThat(account.getTermMonths()).isEqualTo(12);
        assertThat(account.getMaturityDate()).isNotNull();
        assertThat(account.getRolloverOption()).isEqualTo("PRINCIPAL_AND_INTEREST");
    }

    @Test
    @DisplayName("Opening Fixed Deposit account fails if initial deposit is below min_open_amount")
    void testOpenFixedDepositFailsBelowMinOpen() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .fullName("Retail Investor " + suffix)
                .nationalId("ID-RET-" + suffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .build());

        OpenAccountCommand cmd = OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .branchCode("VN001")
                .status("ACTIVE")
                .initialDeposit(new BigDecimal("2000000.0000")) // 2M < min_open 10M
                .termMonths(12)
                .build();

        assertThatThrownBy(() -> accountService.openAccount(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("below minimum opening balance");
    }
}

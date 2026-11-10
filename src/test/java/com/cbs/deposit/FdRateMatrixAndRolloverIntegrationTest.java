package com.cbs.deposit;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.deposit.service.FdRateMatrixService;
import com.cbs.common.codelist.controller.CodeListApiController;
import com.cbs.common.codelist.dto.CodeListValueDto;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.eod.service.FdMaturityRolloverService;
import com.cbs.form.controller.FormApiController;
import com.cbs.form.dto.FormEvaluateRequest;
import com.cbs.form.dto.FormEvaluateResponse;
import com.cbs.admin.service.WorkingDateService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class FdRateMatrixAndRolloverIntegrationTest {

    @Autowired
    private FormApiController formApiController;

    @Autowired
    private CodeListApiController codeListApiController;

    @Autowired
    private FdRateMatrixService fdRateMatrixService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private FdMaturityRolloverService fdMaturityRolloverService;

    @Autowired
    private WorkingDateService workingDateService;

    private Customer createTestCustomer(String suffix) {
        String uniqueCif = ("CIF7" + suffix).substring(0, Math.min(10, ("CIF7" + suffix).length()));
        String uniqueId = ("ID7" + suffix).substring(0, Math.min(12, ("ID7" + suffix).length()));

        CreateCustomerCommand cmd = CreateCustomerCommand.builder()
                .fullName("FD Test Customer " + suffix)
                .customerType("INDIVIDUAL")
                .nationalId(uniqueId)
                .kycStatus("VERIFIED")
                .build();
        return customerService.createCustomer(cmd);
    }

    @Test
    @DisplayName("CodeList API returns parameterized options for FD code groups")
    void testCodeListApi() {
        ResponseEntity<List<CodeListValueDto>> rolloverResp = codeListApiController.getCodeListValues("ROLLOVER_OPTION");
        assertThat(rolloverResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rolloverResp.getBody()).isNotNull();
        List<String> rolloverCodes = rolloverResp.getBody().stream().map(CodeListValueDto::codeValue).toList();
        assertThat(rolloverCodes).contains("PRINCIPAL_AND_INTEREST", "PRINCIPAL_ONLY", "NO_ROLLOVER");

        ResponseEntity<List<CodeListValueDto>> rateRuleResp = codeListApiController.getCodeListValues("ROLLOVER_RATE_RULE");
        assertThat(rateRuleResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(rateRuleResp.getBody()).isNotNull();
        List<String> rateRuleCodes = rateRuleResp.getBody().stream().map(CodeListValueDto::codeValue).toList();
        assertThat(rateRuleCodes).contains("CURRENT_MARKET_RATE", "ORIGINAL_CONTRACT_RATE", "MANUAL_CONFIRMATION");

        ResponseEntity<List<CodeListValueDto>> reasonResp = codeListApiController.getCodeListValues("MARGIN_REASON");
        assertThat(reasonResp.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(reasonResp.getBody()).isNotNull();
        List<String> reasonCodes = reasonResp.getBody().stream().map(CodeListValueDto::codeValue).toList();
        assertThat(reasonCodes).contains("VIP_CUSTOMER", "HIGH_BALANCE");
    }

    @Test
    @DisplayName("Form evaluation for FD loads dynamic terms and resolves base rate by term")
    void testEvaluateFdTermsAndRate() {
        Map<String, Object> formData = new HashMap<>();
        formData.put("productCode", "SAV_TERM");
        formData.put("termMonths", "6");

        FormEvaluateRequest request = new FormEvaluateRequest(
                "FORM_ACCOUNT_OPEN",
                "termMonths",
                List.of("EVALUATE_DEPOSIT_PRODUCT"),
                formData
        );

        ResponseEntity<FormEvaluateResponse> response = formApiController.evaluateForm(request);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        FormEvaluateResponse eval = response.getBody();
        assertThat(eval).isNotNull();

        // Verify dynamic term options populated
        assertThat(eval.fieldOptions()).containsKey("termMonths");
        List<Map<String, Object>> termOpts = eval.fieldOptions().get("termMonths");
        assertThat(termOpts).isNotEmpty();
        List<String> termValues = termOpts.stream().map(m -> String.valueOf(m.get("value"))).toList();
        assertThat(termValues).contains("1", "3", "6", "12", "24", "36");

        // Verify 6 months rate is 5.00% (0.0500)
        BigDecimal baseRate = new BigDecimal(eval.updates().get("baseInterestRate").toString());
        assertThat(baseRate).isEqualByComparingTo(new BigDecimal("0.0500"));
    }

    @Test
    @DisplayName("Open FD account resolves matrix rate and calculates maturity date")
    @Transactional
    void testOpenFdAccountWithTermAndMatrixRate() {
        Customer customer = createTestCustomer("OPN1");
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        OpenAccountCommand cmd = OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .branchCode("001")
                .status("ACTIVE")
                .initialDeposit(new BigDecimal("15000000.0000")) // >= 10M min open
                .termMonths(6)
                .rolloverOption("PRINCIPAL_AND_INTEREST")
                .requestInterestMargin(false)
                .build();

        Account fdAccount = accountService.openAccount(cmd);

        assertThat(fdAccount.getProductType()).isEqualTo("FD");
        assertThat(fdAccount.getTermMonths()).isEqualTo(6);
        assertThat(fdAccount.getBaseInterestRate()).isEqualByComparingTo(new BigDecimal("0.0500"));
        assertThat(fdAccount.getEffectiveRate()).isEqualByComparingTo(new BigDecimal("0.0500"));
        assertThat(fdAccount.getMaturityDate()).isEqualTo(workingDate.plusMonths(6));
        assertThat(fdAccount.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("15000000.0000"));
    }

    @Test
    @DisplayName("EOD Rollover: PRINCIPAL_AND_INTEREST capitalizes interest and extends term")
    @Transactional
    void testEodRolloverPrincipalAndInterest() {
        Customer customer = createTestCustomer("ROLP1");
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        Account account = Account.builder()
                .accountNumber("FD_TEST_ROLP1")
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .productType("FD")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("20000000.0000"))
                .accruedInterest(new BigDecimal("550000.0000")) // 550,000 VND interest accrued
                .baseInterestRate(new BigDecimal("0.0550"))
                .interestMargin(BigDecimal.ZERO)
                .effectiveRate(new BigDecimal("0.0550"))
                .termMonths(12)
                .maturityDate(workingDate) // Matures today!
                .rolloverOption("PRINCIPAL_AND_INTEREST")
                .status("ACTIVE")
                .createdWorkingDate(workingDate.minusMonths(12))
                .build();
        account = accountRepository.saveAndFlush(account);

        int processed = fdMaturityRolloverService.executeMaturityRollover(workingDate);
        assertThat(processed).isGreaterThanOrEqualTo(1);

        Account reloaded = accountRepository.findById(account.getAccountId()).orElseThrow();
        // Capitalized: 20M + 550k = 20,550,000 VND
        assertThat(reloaded.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("20550000.0000"));
        assertThat(reloaded.getAccruedInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloaded.getMaturityDate()).isEqualTo(workingDate.plusMonths(12));
        assertThat(reloaded.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("EOD Rollover: PRINCIPAL_ONLY credits interest to linked CASA")
    @Transactional
    void testEodRolloverPrincipalOnly() {
        Customer customer = createTestCustomer("ROLP2");
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        // Create linked CASA
        Account casa = Account.builder()
                .accountNumber("CASA_TEST_ROLP2")
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .productType("CASA")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("1000000.0000"))
                .status("ACTIVE")
                .createdWorkingDate(workingDate)
                .build();
        casa = accountRepository.saveAndFlush(casa);

        Account fdAccount = Account.builder()
                .accountNumber("FD_TEST_ROLP2")
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .productType("FD")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("50000000.0000"))
                .accruedInterest(new BigDecimal("1250000.0000")) // 1,250,000 VND interest
                .baseInterestRate(new BigDecimal("0.0500"))
                .effectiveRate(new BigDecimal("0.0500"))
                .termMonths(6)
                .maturityDate(workingDate) // Matures today
                .rolloverOption("PRINCIPAL_ONLY")
                .linkedCasaAccountId(casa.getAccountId())
                .status("ACTIVE")
                .createdWorkingDate(workingDate.minusMonths(6))
                .build();
        fdAccount = accountRepository.saveAndFlush(fdAccount);

        int processed = fdMaturityRolloverService.executeMaturityRollover(workingDate);
        assertThat(processed).isGreaterThanOrEqualTo(1);

        Account reloadedCasa = accountRepository.findById(casa.getAccountId()).orElseThrow();
        // CASA should receive 1,250,000 interest: 1,000,000 + 1,250,000 = 2,250,000
        assertThat(reloadedCasa.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("2250000.0000"));

        Account reloadedFd = accountRepository.findById(fdAccount.getAccountId()).orElseThrow();
        // FD principal unchanged, interest reset to 0, term shifted
        assertThat(reloadedFd.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("50000000.0000"));
        assertThat(reloadedFd.getAccruedInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloadedFd.getMaturityDate()).isEqualTo(workingDate.plusMonths(6));
        assertThat(reloadedFd.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("EOD Rollover: NO_ROLLOVER with linked CASA closes account and transfers total payout")
    @Transactional
    void testEodRolloverNoRolloverWithCasa() {
        Customer customer = createTestCustomer("ROLP3");
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        Account casa = Account.builder()
                .accountNumber("CASA_TEST_ROLP3")
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .productType("CASA")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("500000.0000"))
                .status("ACTIVE")
                .createdWorkingDate(workingDate)
                .build();
        casa = accountRepository.saveAndFlush(casa);

        Account fdAccount = Account.builder()
                .accountNumber("FD_TEST_ROLP3")
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .productType("FD")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("30000000.0000"))
                .accruedInterest(new BigDecimal("750000.0000"))
                .maturityDate(workingDate)
                .rolloverOption("NO_ROLLOVER")
                .linkedCasaAccountId(casa.getAccountId())
                .status("ACTIVE")
                .createdWorkingDate(workingDate.minusMonths(12))
                .build();
        fdAccount = accountRepository.saveAndFlush(fdAccount);

        int processed = fdMaturityRolloverService.executeMaturityRollover(workingDate);
        assertThat(processed).isGreaterThanOrEqualTo(1);

        Account reloadedCasa = accountRepository.findById(casa.getAccountId()).orElseThrow();
        // CASA receives 30M principal + 750k interest + initial 500k = 31,250,000 VND
        assertThat(reloadedCasa.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("31250000.0000"));

        Account reloadedFd = accountRepository.findById(fdAccount.getAccountId()).orElseThrow();
        assertThat(reloadedFd.getLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloadedFd.getAccruedInterest()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(reloadedFd.getStatus()).isEqualTo("CLOSED");
    }

    @Test
    @DisplayName("EOD Rollover: NO_ROLLOVER without linked CASA transitions status to MATURED")
    @Transactional
    void testEodRolloverNoRolloverWithoutCasa() {
        Customer customer = createTestCustomer("ROLP4");
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        Account fdAccount = Account.builder()
                .accountNumber("FD_TEST_ROLP4")
                .customerId(customer.getCustomerId())
                .productCode("SAV_TERM")
                .productType("FD")
                .glCode("4211")
                .branchCode("001")
                .currency("VND")
                .ledgerBalance(new BigDecimal("10000000.0000"))
                .accruedInterest(new BigDecimal("250000.0000"))
                .maturityDate(workingDate)
                .rolloverOption("NO_ROLLOVER")
                .linkedCasaAccountId(null) // No linked CASA
                .status("ACTIVE")
                .createdWorkingDate(workingDate.minusMonths(12))
                .build();
        fdAccount = accountRepository.saveAndFlush(fdAccount);

        int processed = fdMaturityRolloverService.executeMaturityRollover(workingDate);
        assertThat(processed).isGreaterThanOrEqualTo(1);

        Account reloadedFd = accountRepository.findById(fdAccount.getAccountId()).orElseThrow();
        assertThat(reloadedFd.getStatus()).isEqualTo("MATURED");
        assertThat(reloadedFd.getLedgerBalance()).isEqualByComparingTo(new BigDecimal("10000000.0000"));
    }
}

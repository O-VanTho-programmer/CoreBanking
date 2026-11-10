package com.cbs.customer;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.AccountHold;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.dto.PlaceHoldCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.deposit.service.HoldService;
import com.cbs.common.exception.InsufficientFundsException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class CustomerAndAccountIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("End-to-End: Register customer -> Open CASA account -> Place & Release Hold (Invariant 2)")
    void shouldExecuteCustomerAccountAndHoldLifecycle() {
        // 1. Register Customer
        String uniqueNationalId = "ID_" + UUID.randomUUID().toString().substring(0, 10);
        CreateCustomerCommand custCmd = CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Tran Van Test")
                .nationalId(uniqueNationalId)
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build();

        Customer customer = customerService.createCustomer(custCmd);
        assertThat(customer.getCustomerId()).isNotNull();
        assertThat(customer.getCifNumber()).startsWith("CIF-");

        // 2. Open CASA Account under CASA_VND product
        OpenAccountCommand acctCmd = OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build();

        Account account = accountService.openAccount(acctCmd);
        assertThat(account.getAccountId()).isNotNull();
        assertThat(account.getGlCode()).isEqualTo("4211");
        assertThat(account.getLedgerBalance()).isEqualByComparingTo("0.0000");
        assertThat(account.getHoldBalance()).isEqualByComparingTo("0.0000");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("0.0000");

        // 3. Simulate funding account with 5,000,000 VND ledger balance
        account.setLedgerBalance(new BigDecimal("5000000.0000"));
        accountRepository.save(account);

        // 4. Place Hold: 1,500,000 VND (CARD_AUTH)
        PlaceHoldCommand holdCmd = PlaceHoldCommand.builder()
                .accountId(account.getAccountId())
                .amount(new BigDecimal("1500000.0000"))
                .reasonCode("CARD_AUTH")
                .referenceId("AUTH_VISA_001")
                .build();

        AccountHold hold = holdService.placeHold(holdCmd);
        assertThat(hold.getHoldId()).isNotNull();
        assertThat(hold.getStatus()).isEqualTo("ACTIVE");
        assertThat(hold.getReleasedAt()).isNull();

        Account reloaded = accountService.getAccount(account.getAccountId());
        assertThat(reloaded.getHoldBalance()).isEqualByComparingTo("1500000.0000");
        // Invariant 2: Available = 5M - 1.5M = 3.5M
        assertThat(reloaded.getAvailableBalance()).isEqualByComparingTo("3500000.0000");

        // 5. Attempting to place hold exceeding available balance throws InsufficientFundsException
        PlaceHoldCommand excessiveHoldCmd = PlaceHoldCommand.builder()
                .accountId(account.getAccountId())
                .amount(new BigDecimal("4000000.0000")) // 4M > 3.5M available
                .reasonCode("CARD_AUTH")
                .build();

        assertThatThrownBy(() -> holdService.placeHold(excessiveHoldCmd))
                .isInstanceOf(InsufficientFundsException.class);

        // 6. Release Hold: Restores available balance
        AccountHold releasedHold = holdService.releaseHold(hold.getHoldId());
        assertThat(releasedHold.getStatus()).isEqualTo("RELEASED");
        assertThat(releasedHold.getReleasedAt()).isNotNull();

        Account finalAccount = accountService.getAccount(account.getAccountId());
        assertThat(finalAccount.getHoldBalance()).isEqualByComparingTo("0.0000");
        assertThat(finalAccount.getAvailableBalance()).isEqualByComparingTo("5000000.0000");
    }
}

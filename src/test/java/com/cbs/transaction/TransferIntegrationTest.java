package com.cbs.transaction;

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
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class TransferIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Test
    @DisplayName("End-to-End: Deposit -> Internal Transfer with Fee -> Idempotency Replay -> Invariant 2 Hold Guard")
    void shouldExecuteFullTransferLifecycle() {
        // 1. Setup 2 Customers & CASA Accounts
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Nguyen Van A")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Tran Thi B")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account accA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account accB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 2. Deposit 10,000,000 VND into Account A
        String depKey = "dep-" + UUID.randomUUID();
        TransactionReceipt depReceipt = transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey(depKey)
                .accountNumber(accA.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .branchCode("VN001")
                .currency("VND")
                .makerId("teller01")
                .narration("Nop tien mat ban dau")
                .build());

        assertThat(depReceipt.status()).isEqualTo("COMPLETED");
        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        assertThat(accA.getLedgerBalance()).isEqualByComparingTo("10000000.0000");
        assertThat(accA.getAvailableBalance()).isEqualByComparingTo("10000000.0000");

        // Verify Journal entries for deposit
        List<JournalEntryActive> depEntries = journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(depReceipt.trxId());
        assertThat(depEntries).hasSize(2);
        assertThat(depEntries.get(0).getDrCr()).isEqualTo("D"); // Cash GL
        assertThat(depEntries.get(0).getGlCode()).isEqualTo("1011");
        assertThat(depEntries.get(1).getDrCr()).isEqualTo("C"); // Customer Account
        assertThat(depEntries.get(1).getAccountId()).isEqualTo(accA.getAccountId());

        // 3. Transfer 3,000,000 VND from A to B with 5,000 VND Fee
        String trfKey = "trf-" + UUID.randomUUID();
        TransferCommand trfCmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey(trfKey)
                .fromAccountNumber(accA.getAccountNumber())
                .toAccountNumber(accB.getAccountNumber())
                .amount(new BigDecimal("3000000.0000"))
                .feeAmount(new BigDecimal("5000.0000"))
                .currency("VND")
                .makerId("custA_mobile")
                .narration("Chuyen tien mua hang")
                .build();

        TransactionReceipt trfReceipt = transactionService.transfer(trfCmd);

        assertThat(trfReceipt.status()).isEqualTo("COMPLETED");
        assertThat(trfReceipt.trxNumber()).startsWith("TRX-");

        // Reload accounts from DB
        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        accB = accountRepository.findByAccountNumber(accB.getAccountNumber()).orElseThrow();

        // AccA: 10,000,000 - 3,000,000 - 5,000 = 6,995,000
        assertThat(accA.getLedgerBalance()).isEqualByComparingTo("6995000.0000");
        assertThat(accA.getAvailableBalance()).isEqualByComparingTo("6995000.0000");

        // AccB: 0 + 3,000,000 = 3,000,000
        assertThat(accB.getLedgerBalance()).isEqualByComparingTo("3000000.0000");
        assertThat(accB.getAvailableBalance()).isEqualByComparingTo("3000000.0000");

        // Verify Journal entries for transfer (Group 1: Principal, Group 2: Fee)
        List<JournalEntryActive> trfEntries = journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(trfReceipt.trxId());
        assertThat(trfEntries).hasSize(4);
        // Group 1: Leg 1 DR AccA, Leg 2 CR AccB
        assertThat(trfEntries.get(0).getAccountId()).isEqualTo(accA.getAccountId());
        assertThat(trfEntries.get(0).getDrCr()).isEqualTo("D");
        assertThat(trfEntries.get(0).getAmount()).isEqualByComparingTo("3000000.0000");

        assertThat(trfEntries.get(1).getAccountId()).isEqualTo(accB.getAccountId());
        assertThat(trfEntries.get(1).getDrCr()).isEqualTo("C");
        assertThat(trfEntries.get(1).getAmount()).isEqualByComparingTo("3000000.0000");

        // Group 2: Leg 1 DR AccA (fee), Leg 2 CR SYS_FEE_INCOME
        assertThat(trfEntries.get(2).getAccountId()).isEqualTo(accA.getAccountId());
        assertThat(trfEntries.get(2).getDrCr()).isEqualTo("D");
        assertThat(trfEntries.get(2).getAmount()).isEqualByComparingTo("5000.0000");

        assertThat(trfEntries.get(3).getAccountId()).isNull();
        assertThat(trfEntries.get(3).getDrCr()).isEqualTo("C");
        assertThat(trfEntries.get(3).getAmount()).isEqualByComparingTo("5000.0000");

        // 4. Invariant 5: Idempotency Replay Test
        TransactionReceipt replayReceipt = transactionService.transfer(trfCmd);
        assertThat(replayReceipt.trxId()).isEqualTo(trfReceipt.trxId());
        assertThat(replayReceipt.trxNumber()).isEqualTo(trfReceipt.trxNumber());

        // Balances MUST NOT change on replay
        Account accAAfterReplay = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        assertThat(accAAfterReplay.getLedgerBalance()).isEqualByComparingTo("6995000.0000");

        // 5. Invariant 2 Check: Place Hold then attempt transfer exceeding available balance
        // Ledger = 6,995,000. Place hold of 6,000,000. Available = 995,000.
        AccountHold hold = holdService.placeHold(PlaceHoldCommand.builder()
                .accountId(accA.getAccountId())
                .amount(new BigDecimal("6000000.0000"))
                .reasonCode("CARD_AUTH")
                .referenceId("HOLD-REF-001")
                .build());

        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        assertThat(accA.getHoldBalance()).isEqualByComparingTo("6000000.0000");
        assertThat(accA.getAvailableBalance()).isEqualByComparingTo("995000.0000");

        // Attempt transfer of 1,000,000 VND (Available is only 995,000)
        TransferCommand failedCmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("fail-" + UUID.randomUUID())
                .fromAccountNumber(accA.getAccountNumber())
                .toAccountNumber(accB.getAccountNumber())
                .amount(new BigDecimal("1000000.0000"))
                .makerId("custA_mobile")
                .build();

        assertThatThrownBy(() -> transactionService.transfer(failedCmd))
                .isInstanceOf(InsufficientFundsException.class);

        // Balances remain intact
        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        assertThat(accA.getLedgerBalance()).isEqualByComparingTo("6995000.0000");

        // Release hold and transfer succeeds
        holdService.releaseHold(hold.getHoldId());
        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        assertThat(accA.getAvailableBalance()).isEqualByComparingTo("6995000.0000");

        TransactionReceipt afterReleaseReceipt = transactionService.transfer(failedCmd);
        assertThat(afterReleaseReceipt.status()).isEqualTo("COMPLETED");
    }
}

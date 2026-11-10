package com.cbs.transaction;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.AccountHold;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.dto.PlaceHoldCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.deposit.service.HoldService;
import com.cbs.common.exception.CannotReverseReversalException;
import com.cbs.common.exception.InsufficientFundsException;
import com.cbs.common.exception.TransactionAlreadyReversedException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.ReverseCommand;
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
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class ReversalIntegrationTest {

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
    @DisplayName("End-to-End: Transfer with Fee -> Reversal -> Full Balance Restoration -> Audit Lineage & Guard Checks")
    void shouldExecuteFullReversalLifecycle() {
        // 1. Setup 2 Customers & CASA Accounts
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Sender Customer A")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Receiver Customer B")
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
        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("dep-" + UUID.randomUUID())
                .accountNumber(accA.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .branchCode("VN001")
                .currency("VND")
                .makerId("teller01")
                .build());

        // 3. Transfer 4,000,000 VND from A to B with 5,000 VND fee
        String trfKey = "trf-" + UUID.randomUUID();
        TransactionReceipt trfReceipt = transactionService.transfer(TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey(trfKey)
                .fromAccountNumber(accA.getAccountNumber())
                .toAccountNumber(accB.getAccountNumber())
                .amount(new BigDecimal("4000000.0000"))
                .feeAmount(new BigDecimal("5000.0000"))
                .currency("VND")
                .makerId("custA_mobile")
                .narration("Tien mua xe")
                .build());

        assertThat(trfReceipt.status()).isEqualTo("COMPLETED");

        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        accB = accountRepository.findByAccountNumber(accB.getAccountNumber()).orElseThrow();
        assertThat(accA.getLedgerBalance()).isEqualByComparingTo("5995000.0000");
        assertThat(accB.getLedgerBalance()).isEqualByComparingTo("4000000.0000");

        // 4. Execute Reversal (Storno) of the Transfer
        String revKey = "rev-" + UUID.randomUUID();
        ReverseCommand revCmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey(revKey)
                .originalTrxNumber(trfReceipt.trxNumber())
                .reason("Wrong account number")
                .makerId("teller01")
                .build();

        TransactionReceipt revReceipt = transactionService.reverse(revCmd);

        assertThat(revReceipt).isNotNull();
        assertThat(revReceipt.status()).isEqualTo("COMPLETED");
        assertThat(revReceipt.trxNumber()).startsWith("TRX-");
        assertThat(revReceipt.originalTrxId()).isEqualTo(trfReceipt.trxId());
        assertThat(revReceipt.originalTrxNumber()).isEqualTo(trfReceipt.trxNumber());
        assertThat(revReceipt.reversalReason()).isEqualTo("Wrong account number");

        // 5. Verify Balance Restoration (Exact 100% Return)
        accA = accountRepository.findByAccountNumber(accA.getAccountNumber()).orElseThrow();
        accB = accountRepository.findByAccountNumber(accB.getAccountNumber()).orElseThrow();
        assertThat(accA.getLedgerBalance()).isEqualByComparingTo("10000000.0000");
        assertThat(accA.getAvailableBalance()).isEqualByComparingTo("10000000.0000");
        assertThat(accB.getLedgerBalance()).isEqualByComparingTo("0.0000");
        assertThat(accB.getAvailableBalance()).isEqualByComparingTo("0.0000");

        // 6. Verify Database Audit Lineage in TRX_JOURNAL_ACTIVE
        TrxJournalActive originalTrx = trxJournalActiveRepository.findByTrxNumber(trfReceipt.trxNumber()).orElseThrow();
        assertThat(originalTrx.getIsReversed()).isEqualTo("Y");

        TrxJournalActive reversalTrx = trxJournalActiveRepository.findByTrxNumber(revReceipt.trxNumber()).orElseThrow();
        assertThat(reversalTrx.getStatus()).isEqualTo("COMPLETED");
        assertThat(reversalTrx.getOriginalTrxId()).isEqualTo(originalTrx.getTrxId());
        assertThat(reversalTrx.getReversalReason()).isEqualTo("Wrong account number");
        assertThat(reversalTrx.getIsReversed()).isEqualTo("N");

        // 7. Verify Inverted Journal Legs
        List<JournalEntryActive> revLegs = journalEntryActiveRepository
                .findByTrxIdOrderByEntryGroupAscEntryIndexAsc(reversalTrx.getTrxId());
        assertThat(revLegs).hasSize(4);

        // Group 1: Principal - AccA is Credited 4M, AccB is Debited 4M
        assertThat(revLegs.get(0).getAccountId()).isEqualTo(accA.getAccountId());
        assertThat(revLegs.get(0).getDrCr()).isEqualTo("C"); // Inverted from D
        assertThat(revLegs.get(0).getAmount()).isEqualByComparingTo("4000000.0000");

        assertThat(revLegs.get(1).getAccountId()).isEqualTo(accB.getAccountId());
        assertThat(revLegs.get(1).getDrCr()).isEqualTo("D"); // Inverted from C
        assertThat(revLegs.get(1).getAmount()).isEqualByComparingTo("4000000.0000");

        // Group 2: Fee - AccA is Credited 5k, Fee Income is Debited 5k
        assertThat(revLegs.get(2).getAccountId()).isEqualTo(accA.getAccountId());
        assertThat(revLegs.get(2).getDrCr()).isEqualTo("C"); // Inverted from D
        assertThat(revLegs.get(2).getAmount()).isEqualByComparingTo("5000.0000");

        assertThat(revLegs.get(3).getAccountId()).isNull();
        assertThat(revLegs.get(3).getDrCr()).isEqualTo("D"); // Inverted from C
        assertThat(revLegs.get(3).getAmount()).isEqualByComparingTo("5000.0000");

        // 8. Invariant 5: Idempotent Replay of Reversal
        TransactionReceipt replayReceipt = transactionService.reverse(revCmd);
        assertThat(replayReceipt.trxId()).isEqualTo(revReceipt.trxId());
        assertThat(replayReceipt.trxNumber()).isEqualTo(revReceipt.trxNumber());

        // 9. Guard: Double-Reversal Prevention
        ReverseCommand duplicateRevCmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey("rev-dup-" + UUID.randomUUID())
                .originalTrxNumber(trfReceipt.trxNumber())
                .reason("Another reversal attempt")
                .makerId("teller01")
                .build();

        assertThatThrownBy(() -> transactionService.reverse(duplicateRevCmd))
                .isInstanceOf(TransactionAlreadyReversedException.class);

        // 10. Guard: Cannot Reverse a Reversal
        ReverseCommand reverseTheReversalCmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey("rev-rev-" + UUID.randomUUID())
                .originalTrxNumber(revReceipt.trxNumber())
                .reason("Trying to reverse the reversal")
                .makerId("teller01")
                .build();

        assertThatThrownBy(() -> transactionService.reverse(reverseTheReversalCmd))
                .isInstanceOf(CannotReverseReversalException.class);
    }

    @Test
    @DisplayName("Invariant 2 Guard: Reversal fails if beneficiary has insufficient available balance (e.g. Hold active)")
    void shouldRejectReversalWhenBeneficiaryHasInsufficientAvailableBalance() {
        Customer cust1 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Hold Sender")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer cust2 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Hold Receiver")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account acc1 = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(cust1.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account acc2 = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(cust2.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // Deposit 5,000,000 to acc1
        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("dep-" + UUID.randomUUID())
                .accountNumber(acc1.getAccountNumber())
                .amount(new BigDecimal("5000000.0000"))
                .branchCode("VN001")
                .makerId("teller01")
                .build());

        // Transfer 3,000,000 from acc1 to acc2
        TransactionReceipt trf = transactionService.transfer(TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("trf-" + UUID.randomUUID())
                .fromAccountNumber(acc1.getAccountNumber())
                .toAccountNumber(acc2.getAccountNumber())
                .amount(new BigDecimal("3000000.0000"))
                .makerId("cust1")
                .build());

        // Beneficiary acc2 has 3,000,000. Place hold of 2,500,000 on acc2. Available is only 500,000.
        AccountHold hold = holdService.placeHold(PlaceHoldCommand.builder()
                .accountId(acc2.getAccountId())
                .amount(new BigDecimal("2500000.0000"))
                .reasonCode("CARD_AUTH")
                .referenceId("HOLD-REV-001")
                .build());

        acc2 = accountRepository.findByAccountNumber(acc2.getAccountNumber()).orElseThrow();
        assertThat(acc2.getAvailableBalance()).isEqualByComparingTo("500000.0000");

        // Attempting to reverse the 3,000,000 transfer must fail because acc2 only has 500,000 available
        ReverseCommand revCmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey("rev-" + UUID.randomUUID())
                .originalTrxNumber(trf.trxNumber())
                .reason("Test Hold Guard")
                .makerId("teller01")
                .build();

        assertThatThrownBy(() -> transactionService.reverse(revCmd))
                .isInstanceOf(InsufficientFundsException.class);

        // Balances remain intact
        acc1 = accountRepository.findByAccountNumber(acc1.getAccountNumber()).orElseThrow();
        acc2 = accountRepository.findByAccountNumber(acc2.getAccountNumber()).orElseThrow();
        assertThat(acc1.getLedgerBalance()).isEqualByComparingTo("2000000.0000");
        assertThat(acc2.getLedgerBalance()).isEqualByComparingTo("3000000.0000");

        // Release hold on acc2 -> Available returns to 3,000,000 -> Reversal succeeds!
        holdService.releaseHold(hold.getHoldId());

        TransactionReceipt successRev = transactionService.reverse(revCmd);
        assertThat(successRev.status()).isEqualTo("COMPLETED");

        acc1 = accountRepository.findByAccountNumber(acc1.getAccountNumber()).orElseThrow();
        acc2 = accountRepository.findByAccountNumber(acc2.getAccountNumber()).orElseThrow();
        assertThat(acc1.getLedgerBalance()).isEqualByComparingTo("5000000.0000");
        assertThat(acc2.getLedgerBalance()).isEqualByComparingTo("0.0000");
    }

    @Test
    @DisplayName("Concurrency: Concurrent attempts to reverse the same transaction results in exactly 1 success and 0 double-reversals")
    void shouldHandleConcurrentReversalAttemptsCleanly() throws InterruptedException {
        Customer c1 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Concurrent Sender")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer c2 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Concurrent Receiver")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account a1 = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(c1.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account a2 = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(c2.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("dep-" + UUID.randomUUID())
                .accountNumber(a1.getAccountNumber())
                .amount(new BigDecimal("5000000.0000"))
                .branchCode("VN001")
                .makerId("admin")
                .build());

        TransactionReceipt trf = transactionService.transfer(TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("trf-" + UUID.randomUUID())
                .fromAccountNumber(a1.getAccountNumber())
                .toAccountNumber(a2.getAccountNumber())
                .amount(new BigDecimal("1000000.0000"))
                .makerId("user")
                .build());

        int threadCount = 10;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(threadCount);

        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger alreadyReversedCounter = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.reverse(ReverseCommand.builder()
                            .channelId("BR")
                            .idempotencyKey("concurrent-rev-" + index + "-" + UUID.randomUUID())
                            .originalTrxNumber(trf.trxNumber())
                            .reason("Concurrent race test")
                            .makerId("teller")
                            .build());
                    successCounter.incrementAndGet();
                } catch (TransactionAlreadyReversedException e) {
                    alreadyReversedCounter.incrementAndGet();
                } catch (Throwable ignored) {
                } finally {
                    endGate.countDown();
                }
            });
        }

        startGate.countDown();
        boolean completed = endGate.await(30, TimeUnit.SECONDS);
        executor.shutdown();

        assertThat(completed).isTrue();
        // EXACTLY 1 thread must succeed
        assertThat(successCounter.get()).isEqualTo(1);
        // All other 9 threads caught TransactionAlreadyReversedException
        assertThat(alreadyReversedCounter.get()).isEqualTo(threadCount - 1);

        // Balances must be restored exactly once
        a1 = accountRepository.findByAccountNumber(a1.getAccountNumber()).orElseThrow();
        a2 = accountRepository.findByAccountNumber(a2.getAccountNumber()).orElseThrow();
        assertThat(a1.getLedgerBalance()).isEqualByComparingTo("5000000.0000");
        assertThat(a2.getLedgerBalance()).isEqualByComparingTo("0.0000");
    }
}

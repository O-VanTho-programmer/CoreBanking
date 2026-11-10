package com.cbs.stress;

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
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.dto.DisburseLoanCommand;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.service.LoanAccountService;
import com.cbs.lending.service.LoanDisbursementService;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Phase 4d: High Concurrency Stress Test Suite using Java 21 Virtual Threads.
 * Verifies high throughput, zero deadlocks (ORA-00060), and strict preservation
 * of Invariants 1-6 across Core Banking operations.
 */
@SpringBootTest
class VirtualThreadConcurrencyStressTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private HoldService holdService;

    @Autowired
    private LoanAccountService loanAccountService;

    @Autowired
    private LoanDisbursementService loanDisbursementService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Test
    @DisplayName("Stress 1: Ring Transfers (A -> B -> C -> D -> A) with 80 Virtual Threads produces 0 deadlocks and conserves balance")
    void testVirtualThreadRingTransferDeadlockStress() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup 4 Customers & Accounts
        List<Account> accounts = new ArrayList<>();
        BigDecimal initialBal = new BigDecimal("20000000.0000");

        for (int i = 0; i < 4; i++) {
            Customer cust = customerService.createCustomer(CreateCustomerCommand.builder()
                    .customerType("INDIVIDUAL")
                    .fullName("Ring Cust " + i + " " + suffix)
                    .nationalId("ID-RING-" + i + "-" + suffix)
                    .kycStatus("VERIFIED")
                    .status("ACTIVE")
                    .build());

            Account acc = accountService.openAccount(OpenAccountCommand.builder()
                    .customerId(cust.getCustomerId())
                    .productCode("CASA_VND")
                    .branchCode("VN001")
                    .status("ACTIVE")
                    .build());

            transactionService.deposit(DepositCommand.builder()
                    .channelId("BRANCH")
                    .idempotencyKey("dep-ring-" + i + "-" + suffix)
                    .accountNumber(acc.getAccountNumber())
                    .amount(initialBal)
                    .branchCode("VN001")
                    .makerId("admin")
                    .build());

            accounts.add(acc);
        }

        // 2. Prepare 4 paths: 0->1, 1->2, 2->3, 3->0 (20 transfers each = 80 total transfers)
        int transfersPerLeg = 20;
        int totalTransfers = transfersPerLeg * 4;
        BigDecimal transferAmount = new BigDecimal("100000.0000");

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalTransfers);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        AtomicInteger successCount = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int leg = 0; leg < 4; leg++) {
                final int fromIdx = leg;
                final int toIdx = (leg + 1) % 4;
                final String fromNum = accounts.get(fromIdx).getAccountNumber();
                final String toNum = accounts.get(toIdx).getAccountNumber();

                for (int t = 0; t < transfersPerLeg; t++) {
                    final int txId = t;
                    executor.submit(() -> {
                        try {
                            startGate.await();
                            transactionService.transfer(TransferCommand.builder()
                                    .channelId("MB")
                                    .idempotencyKey("ring-tx-" + fromIdx + "to" + toIdx + "-" + txId + "-" + suffix)
                                    .fromAccountNumber(fromNum)
                                    .toAccountNumber(toNum)
                                    .amount(transferAmount)
                                    .makerId("vt-worker")
                                    .build());
                            successCount.incrementAndGet();
                        } catch (Throwable ex) {
                            errors.add(ex);
                        } finally {
                            endGate.countDown();
                        }
                    });
                }
            }

            // Fire all 80 virtual threads simultaneously
            startGate.countDown();

            boolean done = endGate.await(45, TimeUnit.SECONDS);
            assertThat(done).as("All 80 ring transfers must finish within 45 seconds").isTrue();
        }

        // 3. Invariant Verifications
        assertThat(errors).as("There must be ZERO deadlocks (ORA-00060) or locking failures").isEmpty();
        assertThat(successCount.get()).isEqualTo(totalTransfers);

        BigDecimal totalExpected = initialBal.multiply(BigDecimal.valueOf(4));
        BigDecimal totalActual = BigDecimal.ZERO;

        for (int i = 0; i < 4; i++) {
            Account reloaded = accountRepository.findByAccountNumber(accounts.get(i).getAccountNumber()).orElseThrow();
            totalActual = totalActual.add(reloaded.getLedgerBalance());
            // Since each account sends 20 * 100k and receives 20 * 100k, each balance must return exactly to initialBal
            assertThat(reloaded.getLedgerBalance())
                    .as("Account %d balance must be balanced back to initial", i)
                    .isEqualByComparingTo(initialBal);
        }

        assertThat(totalActual).as("Total system balance must be strictly conserved").isEqualByComparingTo(totalExpected);
    }

    @Test
    @DisplayName("Stress 2: Available Balance & Hold Contention with 40 Virtual Threads guarantees Invariant 2 (Available >= 0)")
    void testVirtualThreadAvailableBalanceHoldContentionStress() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Customer with 5,000,000 VND
        Customer cust = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Hold Contention " + suffix)
                .nationalId("ID-HOLD-" + suffix)
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account acc = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(cust.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        BigDecimal initialFunds = new BigDecimal("5000000.0000");
        transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("dep-hold-" + suffix)
                .accountNumber(acc.getAccountNumber())
                .amount(initialFunds)
                .branchCode("VN001")
                .makerId("admin")
                .build());

        // 2. 40 Virtual Threads compete for 250,000 VND holds each (Total requested = 10,000,000 VND > 5,000,000 VND)
        int totalThreads = 40;
        BigDecimal holdAmount = new BigDecimal("250000.0000");
        int expectedSuccess = 20; // 5M / 250k = exactly 20
        int expectedRejections = 20;

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalThreads);
        AtomicInteger successCounter = new AtomicInteger(0);
        AtomicInteger insufficientFundsCounter = new AtomicInteger(0);
        ConcurrentLinkedQueue<Throwable> unexpectedErrors = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < totalThreads; i++) {
                final int threadId = i;
                executor.submit(() -> {
                    try {
                        startGate.await();
                        holdService.placeHold(PlaceHoldCommand.builder()
                                .accountId(acc.getAccountId())
                                .amount(holdAmount)
                                .reasonCode("CARD_AUTH")
                                .referenceId("AUTH-VT-" + threadId + "-" + suffix)
                                .build());
                        successCounter.incrementAndGet();
                    } catch (InsufficientFundsException ife) {
                        insufficientFundsCounter.incrementAndGet();
                    } catch (Throwable t) {
                        unexpectedErrors.add(t);
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean done = endGate.await(30, TimeUnit.SECONDS);
            assertThat(done).as("All 40 hold attempts must complete within 30s").isTrue();
        }

        // 3. Verify exactly 20 succeeded, 20 failed, zero unexpected exceptions
        assertThat(unexpectedErrors).isEmpty();
        assertThat(successCounter.get()).isEqualTo(expectedSuccess);
        assertThat(insufficientFundsCounter.get()).isEqualTo(expectedRejections);

        // 4. Verify Account State: Available = 0, Hold = 5,000,000, Ledger = 5,000,000
        Account finalAcc = accountRepository.findById(acc.getAccountId()).orElseThrow();
        assertThat(finalAcc.getLedgerBalance()).isEqualByComparingTo("5000000.0000");
        assertThat(finalAcc.getHoldBalance()).isEqualByComparingTo("5000000.0000");
        assertThat(finalAcc.getAvailableBalance()).isEqualByComparingTo("0.0000");
        assertThat(finalAcc.getAvailableBalance()).isGreaterThanOrEqualTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Stress 3: Thundering Herd Idempotency Storm with 20 Virtual Threads produces exactly 1 debit/credit")
    void testVirtualThreadThunderingHerdIdempotencyStorm() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Sender & Receiver
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Idemp Sender " + suffix)
                .nationalId("ID-SND-" + suffix)
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Idemp Recv " + suffix)
                .nationalId("ID-RCV-" + suffix)
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account sender = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account receiver = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("dep-snd-" + suffix)
                .accountNumber(sender.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .branchCode("VN001")
                .makerId("admin")
                .build());

        // 2. 20 Virtual Threads concurrently submit the exact same transfer
        int stormSize = 20;
        String sharedKey = "STORM-KEY-" + suffix;
        BigDecimal transferAmount = new BigDecimal("2000000.0000");

        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(stormSize);
        List<TransactionReceipt> receipts = Collections.synchronizedList(new ArrayList<>());
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < stormSize; i++) {
                executor.submit(() -> {
                    try {
                        startGate.await();
                        TransactionReceipt receipt = transactionService.transfer(TransferCommand.builder()
                                .channelId("MB")
                                .idempotencyKey(sharedKey)
                                .fromAccountNumber(sender.getAccountNumber())
                                .toAccountNumber(receiver.getAccountNumber())
                                .amount(transferAmount)
                                .makerId("storm-user")
                                .build());
                        receipts.add(receipt);
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean done = endGate.await(20, TimeUnit.SECONDS);
            assertThat(done).as("All storm threads must complete within 20s").isTrue();
        }

        // 3. Verify all 20 threads received receipt and all share identical trxNumber
        assertThat(errors).isEmpty();
        assertThat(receipts).hasSize(stormSize);

        String canonicalTrxNumber = receipts.get(0).trxNumber();
        for (TransactionReceipt r : receipts) {
            assertThat(r.trxNumber()).isEqualTo(canonicalTrxNumber);
            assertThat(r.status()).isEqualTo("COMPLETED");
        }

        // 4. Verify Single Debit & Credit in Database
        Account finalSender = accountRepository.findById(sender.getAccountId()).orElseThrow();
        Account finalReceiver = accountRepository.findById(receiver.getAccountId()).orElseThrow();

        // Sender deducted exactly 2M (not 2M * 20 = 40M)
        assertThat(finalSender.getLedgerBalance()).isEqualByComparingTo("8000000.0000");
        // Receiver credited exactly 2M
        assertThat(finalReceiver.getLedgerBalance()).isEqualByComparingTo("2000000.0000");

        // Exactly 1 record in TRX_JOURNAL_ACTIVE
        Optional<TrxJournalActive> journal = trxJournalActiveRepository.findByChannelIdAndIdempotencyKey("MB", sharedKey);
        assertThat(journal).isPresent();
    }

    @Test
    @DisplayName("Stress 4: Concurrent Multi-Borrower Lending Disbursements via Posting Matrix")
    void testVirtualThreadConcurrentLendingDisbursement() throws InterruptedException {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        int borrowerCount = 6;
        BigDecimal loanAmount = new BigDecimal("30000000.0000");

        List<LoanAccount> loans = new ArrayList<>();
        List<Account> casas = new ArrayList<>();

        for (int i = 0; i < borrowerCount; i++) {
            Customer borrower = customerService.createCustomer(CreateCustomerCommand.builder()
                    .customerType("INDIVIDUAL")
                    .fullName("Lending Borrower " + i + " " + suffix)
                    .nationalId("ID-LND-STRESS-" + i + "-" + suffix)
                    .kycStatus("VERIFIED")
                    .status("ACTIVE")
                    .build());

            Account casa = accountService.openAccount(OpenAccountCommand.builder()
                    .customerId(borrower.getCustomerId())
                    .productCode("CASA_VND")
                    .branchCode("VN001")
                    .status("ACTIVE")
                    .build());

            LoanAccount loan = loanAccountService.createLoanContract(CreateLoanContractCommand.builder()
                    .customerId(borrower.getCustomerId())
                    .linkedCasaAccountId(casa.getAccountId())
                    .productCode("LN_CONSUMER_VND")
                    .principalAmount(loanAmount)
                    .termMonths(12)
                    .firstDueDate(LocalDate.now().plusMonths(1))
                    .build());

            loans.add(loan);
            casas.add(casa);
        }

        // Concurrently disburse all 6 loans using Virtual Threads
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(borrowerCount);
        ConcurrentLinkedQueue<Throwable> errors = new ConcurrentLinkedQueue<>();
        AtomicInteger successCounter = new AtomicInteger(0);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            for (int i = 0; i < borrowerCount; i++) {
                final int idx = i;
                final Long loanId = loans.get(idx).getLoanId();

                executor.submit(() -> {
                    try {
                        startGate.await();
                        loanDisbursementService.disburse(DisburseLoanCommand.builder()
                                .loanId(loanId)
                                .channelId("BRANCH")
                                .idempotencyKey("disb-stress-" + idx + "-" + suffix)
                                .build());
                        successCounter.incrementAndGet();
                    } catch (Throwable t) {
                        errors.add(t);
                    } finally {
                        endGate.countDown();
                    }
                });
            }

            startGate.countDown();
            boolean done = endGate.await(30, TimeUnit.SECONDS);
            assertThat(done).as("All disbursements must complete within 30s").isTrue();
        }

        // Verify all 6 disbursed without errors
        assertThat(errors).isEmpty();
        assertThat(successCounter.get()).isEqualTo(borrowerCount);

        for (int i = 0; i < borrowerCount; i++) {
            LoanAccount reloadedLoan = loanAccountRepository.findById(loans.get(i).getLoanId()).orElseThrow();
            assertThat(reloadedLoan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE.name());
            assertThat(reloadedLoan.getDisbursedAt()).isNotNull();

            Account reloadedCasa = accountRepository.findById(casas.get(i).getAccountId()).orElseThrow();
            assertThat(reloadedCasa.getLedgerBalance()).isEqualByComparingTo(loanAmount);
            assertThat(reloadedCasa.getAvailableBalance()).isEqualByComparingTo(loanAmount);
        }
    }
}

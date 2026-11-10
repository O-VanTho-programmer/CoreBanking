package com.cbs.transaction;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class DeadlockConcurrencyIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Test
    @DisplayName("Invariant 3: High concurrency opposing transfers (A -> B and B -> A) produce 0 deadlocks and conserve balance")
    void shouldExecuteConcurrentOpposingTransfersWithoutDeadlock() throws InterruptedException {
        // 1. Setup 2 Customers & CASA Accounts
        Customer cust1 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Concurrent Customer 1")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer cust2 = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Concurrent Customer 2")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account accA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(cust1.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account accB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(cust2.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 2. Fund both accounts: 50,000,000 VND each
        BigDecimal initialBalance = new BigDecimal("50000000.0000");
        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("fund-a-" + UUID.randomUUID())
                .accountNumber(accA.getAccountNumber())
                .amount(initialBalance)
                .branchCode("VN001")
                .makerId("admin")
                .build());

        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("fund-b-" + UUID.randomUUID())
                .accountNumber(accB.getAccountNumber())
                .amount(initialBalance)
                .branchCode("VN001")
                .makerId("admin")
                .build());

        // 3. Prepare Concurrency: 25 A -> B transfers and 25 B -> A transfers simultaneously
        int transferCountPerDirection = 25;
        int totalTransfers = transferCountPerDirection * 2;
        BigDecimal transferAmount = new BigDecimal("100000.0000");

        ExecutorService executor = Executors.newFixedThreadPool(16);
        CountDownLatch startGate = new CountDownLatch(1);
        CountDownLatch endGate = new CountDownLatch(totalTransfers);

        ConcurrentLinkedQueue<Throwable> exceptions = new ConcurrentLinkedQueue<>();
        AtomicInteger successCounter = new AtomicInteger(0);

        String accANum = accA.getAccountNumber();
        String accBNum = accB.getAccountNumber();

        // 25 transfers: A -> B
        for (int i = 0; i < transferCountPerDirection; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.transfer(TransferCommand.builder()
                            .channelId("MB")
                            .idempotencyKey("trf-a2b-" + index + "-" + UUID.randomUUID())
                            .fromAccountNumber(accANum)
                            .toAccountNumber(accBNum)
                            .amount(transferAmount)
                            .makerId("thread-a")
                            .build());
                    successCounter.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        // 25 transfers: B -> A
        for (int i = 0; i < transferCountPerDirection; i++) {
            final int index = i;
            executor.submit(() -> {
                try {
                    startGate.await();
                    transactionService.transfer(TransferCommand.builder()
                            .channelId("MB")
                            .idempotencyKey("trf-b2a-" + index + "-" + UUID.randomUUID())
                            .fromAccountNumber(accBNum)
                            .toAccountNumber(accANum)
                            .amount(transferAmount)
                            .makerId("thread-b")
                            .build());
                    successCounter.incrementAndGet();
                } catch (Throwable t) {
                    exceptions.add(t);
                } finally {
                    endGate.countDown();
                }
            });
        }

        // Unleash all 50 threads simultaneously
        startGate.countDown();

        // Wait up to 60 seconds for all transfers to finish
        boolean completedInTime = endGate.await(60, TimeUnit.SECONDS);
        executor.shutdown();

        // 4. Assertions
        assertThat(completedInTime).as("All concurrent transfers should complete within 60s").isTrue();
        assertThat(exceptions).as("There should be zero deadlocks (ORA-00060) or lock errors").isEmpty();
        assertThat(successCounter.get()).isEqualTo(totalTransfers);

        // 5. Total Balance Conservation Check
        Account finalA = accountRepository.findByAccountNumber(accANum).orElseThrow();
        Account finalB = accountRepository.findByAccountNumber(accBNum).orElseThrow();

        BigDecimal expectedTotal = initialBalance.multiply(new BigDecimal(2));
        BigDecimal actualTotal = finalA.getLedgerBalance().add(finalB.getLedgerBalance());

        assertThat(actualTotal).as("Total system balance must be strictly conserved").isEqualByComparingTo(expectedTotal);
        // Since symmetric (25 each way with 0 fee), each account should end up back at initialBalance
        assertThat(finalA.getLedgerBalance()).isEqualByComparingTo(initialBalance);
        assertThat(finalB.getLedgerBalance()).isEqualByComparingTo(initialBalance);
    }
}

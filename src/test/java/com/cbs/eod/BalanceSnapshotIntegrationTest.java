package com.cbs.eod;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.service.AccountService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateSnapshotException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.eod.domain.AccountBalanceBucket;
import com.cbs.eod.domain.AccountBalanceBucketId;
import com.cbs.eod.dto.BalanceSnapshotBatchResult;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.eod.service.BalanceSnapshotService;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BalanceSnapshotIntegrationTest {

    @Autowired
    private BalanceSnapshotService balanceSnapshotService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountBalanceBucketRepository bucketRepository;

    @Autowired
    private WorkingDateService workingDateService;

    private LocalDate snapshotDate;

    @BeforeEach
    void setUp() {
        snapshotDate = workingDateService.getCurrentWorkingDate();
        cleanup();
    }

    @AfterEach
    void tearDown() {
        cleanup();
    }

    private void cleanup() {
        if (snapshotDate != null) {
            bucketRepository.findByWorkingDate(snapshotDate)
                    .forEach(bucketRepository::delete);
        }
    }

    @Test
    @DisplayName("Integration: End-to-end balance snapshot verifies closing balance, turnover, and balance equation")
    void testEndToEndBalanceSnapshotAndEquation() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customer A & B
        Customer customerA = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDA-" + uniqueSuffix)
                .fullName("Customer A " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer customerB = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDB-" + uniqueSuffix)
                .fullName("Customer B " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        // 2. Open Accounts
        Account accountA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(customerA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account accountB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(customerB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 3. Deposit 10,000,000 VND into Account A
        transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("DEP-A-" + uniqueSuffix)
                .accountNumber(accountA.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .makerId("TELLER01")
                .narration("Initial deposit A")
                .build());

        // 4. Transfer 3,000,000 VND from Account A to Account B (Fee = 5,000 VND)
        transactionService.transfer(TransferCommand.builder()
                .channelId("MOBILE")
                .idempotencyKey("TRF-AB-" + uniqueSuffix)
                .fromAccountNumber(accountA.getAccountNumber())
                .toAccountNumber(accountB.getAccountNumber())
                .amount(new BigDecimal("3000000.0000"))
                .feeAmount(new BigDecimal("5000.0000"))
                .makerId("CUST_APP")
                .narration("Transfer A to B")
                .build());

        // Refresh accounts from DB
        Account refreshedA = accountRepository.findById(accountA.getAccountId()).orElseThrow();
        Account refreshedB = accountRepository.findById(accountB.getAccountId()).orElseThrow();

        // Account A: 10,000,000 - 3,000,000 - 5,000 = 6,995,000
        assertThat(refreshedA.getLedgerBalance()).isEqualByComparingTo("6995000.0000");
        // Account B: 3,000,000
        assertThat(refreshedB.getLedgerBalance()).isEqualByComparingTo("3000000.0000");

        // 5. Execute Balance Snapshot Bucket Service
        BalanceSnapshotBatchResult result = balanceSnapshotService.takeDailySnapshot(snapshotDate);

        assertThat(result.workingDate()).isEqualTo(snapshotDate);
        assertThat(result.totalAccountsSnapshotted()).isGreaterThanOrEqualTo(2);
        assertThat(result.totalClosingBalance()).isGreaterThan(BigDecimal.ZERO);

        // 6. Verify Account A's Bucket
        AccountBalanceBucket bucketA = bucketRepository.findById(
                new AccountBalanceBucketId(accountA.getAccountId(), snapshotDate)
        ).orElseThrow();

        assertThat(bucketA.getClosingBalance()).isEqualByComparingTo("6995000.0000");
        assertThat(bucketA.getDailyCreditSum()).isEqualByComparingTo("10000000.0000");
        assertThat(bucketA.getDailyDebitSum()).isEqualByComparingTo("3005000.0000"); // 3M principal + 5k fee
        assertThat(bucketA.getAccruedInterestDay()).isGreaterThan(BigDecimal.ZERO);

        // Verify balance turnover equation for Account A:
        // Opening (0) + Credit (10M) - Debit (3.005M) == Closing (6.995M)
        BigDecimal calculatedClosingA = BigDecimal.ZERO
                .add(bucketA.getDailyCreditSum())
                .subtract(bucketA.getDailyDebitSum());
        assertThat(calculatedClosingA).isEqualByComparingTo(bucketA.getClosingBalance());

        // 7. Verify Account B's Bucket
        AccountBalanceBucket bucketB = bucketRepository.findById(
                new AccountBalanceBucketId(accountB.getAccountId(), snapshotDate)
        ).orElseThrow();

        assertThat(bucketB.getClosingBalance()).isEqualByComparingTo("3000000.0000");
        assertThat(bucketB.getDailyCreditSum()).isEqualByComparingTo("3000000.0000");
        assertThat(bucketB.getDailyDebitSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bucketB.getAccruedInterestDay()).isGreaterThan(BigDecimal.ZERO);

        // Verify balance turnover equation for Account B:
        // Opening (0) + Credit (3M) - Debit (0) == Closing (3M)
        BigDecimal calculatedClosingB = BigDecimal.ZERO
                .add(bucketB.getDailyCreditSum())
                .subtract(bucketB.getDailyDebitSum());
        assertThat(calculatedClosingB).isEqualByComparingTo(bucketB.getClosingBalance());

        // 8. Idempotency Check: Re-running for same date must throw DuplicateSnapshotException
        assertThatThrownBy(() -> balanceSnapshotService.takeDailySnapshot(snapshotDate))
                .isInstanceOf(DuplicateSnapshotException.class)
                .hasMessageContaining("Balance snapshot bucket has already been created for working date: " + snapshotDate);
    }
}

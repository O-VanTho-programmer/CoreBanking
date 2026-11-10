package com.cbs.eod;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.admin.domain.SystemOperationalStatus;
import com.cbs.admin.repository.SystemOperationalStatusRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.eod.domain.AccountBalanceBucketId;
import com.cbs.eod.dto.EodExecutionReport;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.eod.service.EodJobLauncherService;
import com.cbs.transaction.domain.JournalEntryDone;
import com.cbs.transaction.domain.TrxJournalDone;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class EodPipelineIntegrationTest {

    @Autowired
    private EodJobLauncherService eodJobLauncherService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private WorkingDateService workingDateService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private AccountBalanceBucketRepository bucketRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Autowired
    private TrxJournalDoneRepository trxJournalDoneRepository;

    @Autowired
    private JournalEntryDoneRepository journalEntryDoneRepository;

    @Autowired
    private SystemOperationalStatusRepository statusRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            LocalDate testDate = LocalDate.of(2026, 10, 14);
            SystemOperationalStatus opStatus = statusRepository.findById(1L).orElseThrow();
            opStatus.setCurrentWorkingDate(testDate);
            opStatus.setIsSystemCutoff("N");
            statusRepository.save(opStatus);

            // Clean up any test records for testDate to guarantee idempotency across multiple test runs
            jdbcTemplate.update("DELETE FROM ACCOUNT_BALANCE_BUCKET WHERE working_date = ?", testDate);
            jdbcTemplate.update("DELETE FROM JOURNAL_ENTRY_ACTIVE WHERE value_date = ?", testDate);
            jdbcTemplate.update("DELETE FROM TRX_JOURNAL_ACTIVE WHERE working_date = ?", testDate);
            jdbcTemplate.update("DELETE FROM JOURNAL_ENTRY_DONE WHERE value_date = ?", testDate);
            jdbcTemplate.update("DELETE FROM TRX_JOURNAL_DONE WHERE working_date = ?", testDate);
        });
    }

    @AfterEach
    void tearDown() {
        transactionTemplate.executeWithoutResult(status -> {
            workingDateService.disableCutoff();
        });
    }

    @Test
    @DisplayName("Integration: Full End-to-End Spring Batch EOD Pipeline runs all 6 steps successfully")
    void testEndToEndEodBatchPipeline() throws Exception {
        LocalDate initialWorkingDate = workingDateService.getCurrentWorkingDate();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Prepare daytime customer & accounts
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDA-EOD-" + uniqueSuffix)
                .fullName("Customer EOD A " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDB-EOD-" + uniqueSuffix)
                .fullName("Customer EOD B " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account acctA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account acctB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 2. Perform daytime deposit and transfer transactions
        TransactionReceipt depReceipt = transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("DEP-EOD-" + uniqueSuffix)
                .accountNumber(acctA.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .makerId("TELLER01")
                .narration("Deposit for EOD integration test")
                .build());

        TransactionReceipt trfReceipt = transactionService.transfer(TransferCommand.builder()
                .channelId("MOBILE")
                .idempotencyKey("TRF-EOD-" + uniqueSuffix)
                .fromAccountNumber(acctA.getAccountNumber())
                .toAccountNumber(acctB.getAccountNumber())
                .amount(new BigDecimal("2000000.0000"))
                .feeAmount(new BigDecimal("5000.0000"))
                .makerId("CUST_APP")
                .narration("Transfer for EOD integration test")
                .build());

        // 3. Launch Spring Batch EOD Pipeline
        EodExecutionReport report = eodJobLauncherService.runEodPipeline();

        // 4. Verify Job Execution High-Level Report
        assertThat(report.status()).isEqualTo("COMPLETED");
        assertThat(report.previousWorkingDate()).isEqualTo(initialWorkingDate);
        assertThat(report.newWorkingDate()).isAfter(initialWorkingDate);
        assertThat(report.durationMs()).isGreaterThan(0);
        assertThat(report.trialBalanceReport()).isNotNull();
        assertThat(report.trialBalanceReport().isBalanced()).isTrue();

        // 5. Verify Invariant 4: Cutoff is reopened and working date rolled over
        assertThat(workingDateService.isSystemCutoff()).isFalse();
        assertThat(workingDateService.getCurrentWorkingDate()).isEqualTo(report.newWorkingDate());

        // 6. Verify Step 3: ACCOUNT_BALANCE_BUCKET was populated
        assertThat(bucketRepository.findById(
                new AccountBalanceBucketId(acctA.getAccountId(), initialWorkingDate)
        )).isPresent();
        assertThat(bucketRepository.findById(
                new AccountBalanceBucketId(acctB.getAccountId(), initialWorkingDate)
        )).isPresent();

        // 7. Verify Step 4: Hot-to-Cold Data Archival (TRX_JOURNAL_DONE & JOURNAL_ENTRY_DONE)
        assertThat(trxJournalDoneRepository.findById(depReceipt.trxId())).isPresent();
        assertThat(trxJournalDoneRepository.findById(trfReceipt.trxId())).isPresent();

        List<JournalEntryDone> depEntries = journalEntryDoneRepository.findByTrxId(depReceipt.trxId());
        List<JournalEntryDone> trfEntries = journalEntryDoneRepository.findByTrxId(trfReceipt.trxId());
        assertThat(depEntries).hasSize(2);
        assertThat(trfEntries).hasSize(4);

        // Active tables must be clean of archived records
        assertThat(trxJournalActiveRepository.findById(depReceipt.trxId())).isEmpty();
        assertThat(trxJournalActiveRepository.findById(trfReceipt.trxId())).isEmpty();
    }
}

package com.cbs.eod;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateAccrualException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.eod.dto.AccrualBatchResult;
import com.cbs.eod.service.InterestAccrualService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class InterestAccrualIntegrationTest {

    @Autowired
    private InterestAccrualService interestAccrualService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Autowired
    private WorkingDateService workingDateService;

    private final LocalDate accrualDate = LocalDate.of(2026, 11, 1);

    @org.junit.jupiter.api.BeforeEach
    @org.junit.jupiter.api.AfterEach
    void cleanup() {
        trxJournalActiveRepository.findByChannelIdAndIdempotencyKey("BATCH", "ACCRUAL:" + accrualDate)
                .ifPresent(trx -> {
                    List<JournalEntryActive> entries = journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(trx.getTrxId());
                    journalEntryActiveRepository.deleteAll(entries);
                    trxJournalActiveRepository.delete(trx);
                });
    }

    @Test
    @DisplayName("Integration: End-to-end interest accrual updates account and creates balanced GL journal")
    void testEndToEndAccrualAndEquilibrium() {
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customer
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("ID-" + uniqueSuffix)
                .fullName("Accrual Test Customer " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        // 2. Open Account
        Account account = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 3. Deposit funds to create ledger balance (e.g. 10,000,000 VND)
        transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("DEP-" + uniqueSuffix)
                .accountNumber(account.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .makerId("TELLER01")
                .narration("Deposit for accrual test")
                .build());

        // Refresh account
        Account reloadedAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
        assertThat(reloadedAccount.getLedgerBalance()).isEqualByComparingTo("10000000.0000");
        BigDecimal initialAccrued = reloadedAccount.getAccruedInterest();

        // 4. Run Daily Interest Accrual for a future distinct working date to avoid idempotency collision
        AccrualBatchResult result = interestAccrualService.executeDailyAccrual(accrualDate);

        // Verify summary result
        assertThat(result.workingDate()).isEqualTo(accrualDate);
        assertThat(result.totalAccountsProcessed()).isGreaterThanOrEqualTo(1);
        assertThat(result.totalAccountsAccrued()).isGreaterThanOrEqualTo(1);
        assertThat(result.totalInterestAccrued()).isGreaterThan(BigDecimal.ZERO);
        assertThat(result.journalTrxId()).isNotNull();
        assertThat(result.trxNumber()).startsWith("ACCRUAL-20261101-");

        // Verify Account accrued_interest was incremented
        Account postAccrualAccount = accountRepository.findById(account.getAccountId()).orElseThrow();
        // 10,000,000 * 0.0050 / 365 = 136.9863
        BigDecimal expectedDailyInterest = new BigDecimal("136.9863");
        assertThat(postAccrualAccount.getAccruedInterest())
                .isEqualByComparingTo(initialAccrued.add(expectedDailyInterest));

        // 5. Verify Invariant 1: Double-Entry Equilibrium on JOURNAL_ENTRY_ACTIVE
        TrxJournalActive journal = trxJournalActiveRepository.findById(result.journalTrxId()).orElseThrow();
        assertThat(journal.getStatus()).isEqualTo("COMPLETED");
        assertThat(journal.getChannelId()).isEqualTo("BATCH");
        assertThat(journal.getTrxCode()).isEqualTo("EOD_ACCRUAL");
        assertThat(journal.getWorkingDate()).isEqualTo(accrualDate);

        List<JournalEntryActive> entries = journalEntryActiveRepository.findAll().stream()
                .filter(e -> e.getTrxId().equals(journal.getTrxId()))
                .toList();

        assertThat(entries).hasSize(2);

        JournalEntryActive debitLeg = entries.stream().filter(e -> "D".equals(e.getDrCr())).findFirst().orElseThrow();
        JournalEntryActive creditLeg = entries.stream().filter(e -> "C".equals(e.getDrCr())).findFirst().orElseThrow();

        assertThat(debitLeg.getGlCode()).isEqualTo("8011"); // SYS_INT_EXPENSE
        assertThat(creditLeg.getGlCode()).isEqualTo("4911"); // SYS_INT_PAYABLE

        assertThat(debitLeg.getAmount()).isEqualByComparingTo(result.totalInterestAccrued());
        assertThat(creditLeg.getAmount()).isEqualByComparingTo(result.totalInterestAccrued());
        assertThat(debitLeg.getAmount()).isEqualByComparingTo(creditLeg.getAmount());

        // 6. Verify Idempotency: Re-running for same accrualDate must fail
        assertThatThrownBy(() -> interestAccrualService.executeDailyAccrual(accrualDate))
                .isInstanceOf(DuplicateAccrualException.class)
                .hasMessageContaining("Daily interest accrual has already been executed for working date: 2026-11-01");
    }
}

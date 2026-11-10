package com.cbs.lending;

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
import com.cbs.eod.dto.EodExecutionReport;
import com.cbs.eod.service.EodJobLauncherService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.AutoRepaymentBatchResult;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.dto.DisburseLoanCommand;
import com.cbs.lending.dto.LoanAgingBatchResult;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import com.cbs.lending.service.LoanAccountService;
import com.cbs.lending.service.LoanAgingService;
import com.cbs.lending.service.LoanDisbursementService;
import com.cbs.lending.service.LoanRepaymentService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
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
class EodLendingIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private LoanAccountService loanAccountService;

    @Autowired
    private LoanDisbursementService loanDisbursementService;

    @Autowired
    private LoanRepaymentService loanRepaymentService;

    @Autowired
    private LoanAgingService loanAgingService;

    @Autowired
    private EodJobLauncherService eodJobLauncherService;

    @Autowired
    private WorkingDateService workingDateService;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private LoanScheduleRepository loanScheduleRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Autowired
    private SystemOperationalStatusRepository statusRepository;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final LocalDate testDate = LocalDate.of(2026, 12, 10);

    @BeforeEach
    void setUp() {
        transactionTemplate.executeWithoutResult(status -> {
            SystemOperationalStatus opStatus = statusRepository.findById(1L).orElseThrow();
            opStatus.setCurrentWorkingDate(testDate);
            opStatus.setIsSystemCutoff("N");
            statusRepository.save(opStatus);

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
    @DisplayName("Phase 4c E2E: Disbursement -> Auto-Repayment -> Aging -> Dual Provisioning -> Reversal to 7911 -> EOD Pipeline")
    void testEndToEndLendingEodLifecycle() throws Exception {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Setup Customer and Linked CASA Account
        Customer borrower = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("ID-LND-" + uniqueSuffix)
                .fullName("Borrower EOD " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account casaAccount = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(borrower.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 2. Create Loan Contract (12,000,000 VND, 12 months)
        LoanAccount loan = loanAccountService.createLoanContract(CreateLoanContractCommand.builder()
                .customerId(borrower.getCustomerId())
                .linkedCasaAccountId(casaAccount.getAccountId())
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("12000000.0000"))
                .termMonths(12)
                .firstDueDate(testDate)
                .build());

        // 3. Disburse Loan via Posting Matrix (DR 2111 / CR 4211: 12,000,000 VND)
        loanDisbursementService.disburse(DisburseLoanCommand.builder()
                .loanId(loan.getLoanId())
                .channelId("BRANCH")
                .idempotencyKey("DISB-EOD-" + uniqueSuffix)
                .makerId("OFFICER01")
                .narrationOverride("Disbursement for lending EOD test")
                .build());

        // Refresh CASA Account
        casaAccount = accountRepository.findById(casaAccount.getAccountId()).orElseThrow();
        assertThat(casaAccount.getLedgerBalance()).isEqualByComparingTo("12000000.0000");

        // 4. Update Installment 1 Schedule to be due on testDate
        List<LoanSchedule> schedules = loanScheduleRepository.findByLoanIdOrderByPeriodNumberAsc(loan.getLoanId());
        LoanSchedule schedule1 = schedules.get(0);
        schedule1.setDueDate(testDate);
        loanScheduleRepository.save(schedule1);

        BigDecimal prDue = schedule1.getPrincipalDue();
        BigDecimal inDue = schedule1.getInterestDue();
        BigDecimal totalInstallment = prDue.add(inDue);

        // 5. Execute Auto-Repayment (Waterfall: Interest -> Principal)
        AutoRepaymentBatchResult repayResult = loanRepaymentService.executeAutoRepayment(testDate);
        assertThat(repayResult.fullyPaidSchedulesCount()).isGreaterThanOrEqualTo(1);
        assertThat(repayResult.totalPrincipalCollected()).isGreaterThan(BigDecimal.ZERO);
        assertThat(repayResult.totalInterestCollected()).isGreaterThan(BigDecimal.ZERO);

        schedule1 = loanScheduleRepository.findById(schedule1.getScheduleId()).orElseThrow();
        assertThat(schedule1.getStatus()).isEqualTo(LoanScheduleStatus.PAID.name());

        loan = loanAccountRepository.findById(loan.getLoanId()).orElseThrow();
        BigDecimal remainingAfterRepay = loan.getRemainingPrincipal();
        assertThat(remainingAfterRepay).isEqualByComparingTo(new BigDecimal("12000000.0000").subtract(prDue));

        // 6. Execute Aging & Provisioning (Group 1: 0.75% General Provision)
        LoanAgingBatchResult agingResult1 = loanAgingService.executeAgingAndProvisioning(testDate);
        assertThat(agingResult1.totalGeneralProvisionCalculated()).isGreaterThan(BigDecimal.ZERO);

        loan = loanAccountRepository.findById(loan.getLoanId()).orElseThrow();
        assertThat(loan.getLoanGroup()).isEqualTo(1);
        assertThat(loan.getDaysPastDue()).isEqualTo(0);
        assertThat(loan.getGeneralProvisionAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(loan.getSpecificProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo(loan.getGeneralProvisionAmount());

        // 7. Simulate Past-Due Schedule & Transition to Group 2 (DPD = 15)
        LoanSchedule schedule2 = schedules.get(1);
        schedule2.setDueDate(testDate.minusDays(15));
        loanScheduleRepository.save(schedule2);

        LoanAgingBatchResult agingResult2 = loanAgingService.executeAgingAndProvisioning(testDate);
        assertThat(agingResult2.overdueLoansCount()).isGreaterThanOrEqualTo(1);
        assertThat(agingResult2.classifiedGroup2Count()).isGreaterThanOrEqualTo(1);
        assertThat(agingResult2.totalOverduePrincipalTransferred()).isGreaterThan(BigDecimal.ZERO);
        assertThat(agingResult2.totalSpecificProvisionCalculated()).isGreaterThan(BigDecimal.ZERO);

        loan = loanAccountRepository.findById(loan.getLoanId()).orElseThrow();
        assertThat(loan.getLoanGroup()).isEqualTo(2);
        assertThat(loan.getDaysPastDue()).isEqualTo(15);
        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE.name());
        assertThat(loan.getSpecificProvisionAmount()).isGreaterThan(BigDecimal.ZERO);
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo(
                loan.getGeneralProvisionAmount().add(loan.getSpecificProvisionAmount())
        );

        // 8. Release Provisions upon Paid-Off to SYS_PROV_REVERSAL_INCOME (TK 7911)
        loan.setLoanStatus(LoanStatus.PAID_OFF.name());
        loan.setRemainingPrincipal(BigDecimal.ZERO);
        loanAccountRepository.save(loan);

        loanAgingService.releaseProvisionsOnPaidOff(loan.getLoanId(), testDate);

        loan = loanAccountRepository.findById(loan.getLoanId()).orElseThrow();
        assertThat(loan.getGeneralProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getSpecificProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // Verify Journal Entries for Provision Reversal include GL 7911
        List<JournalEntryActive> reversalLegs = journalEntryActiveRepository.findAll().stream()
                .filter(e -> "7911".equals(e.getGlCode()))
                .toList();
        assertThat(reversalLegs).isNotEmpty();
        assertThat(reversalLegs.get(0).getDrCr()).isEqualTo("C"); // Credit Revenue 7911

        // 9. Execute Full Spring Batch 8-Step EOD Pipeline & Verify Trial Balance
        EodExecutionReport eodReport = eodJobLauncherService.runEodPipeline();

        assertThat(eodReport.status()).isEqualTo("COMPLETED");
        assertThat(eodReport.trialBalanceReport()).isNotNull();
        assertThat(eodReport.trialBalanceReport().isBalanced()).isTrue();
        assertThat(eodReport.trialBalanceReport().macroDebitSum())
                .isEqualByComparingTo(eodReport.trialBalanceReport().macroCreditSum());
    }
}

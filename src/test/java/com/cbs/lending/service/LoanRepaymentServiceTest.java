package com.cbs.lending.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.AutoRepaymentBatchResult;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanRepaymentServiceTest {

    @Mock
    private LoanScheduleRepository loanScheduleRepository;

    @Mock
    private LoanAccountRepository loanAccountRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoanRepaymentService loanRepaymentService;

    private LoanAccount loan;
    private Account casaAccount;
    private LoanSchedule dueSchedule;
    private final LocalDate workingDate = LocalDate.of(2026, 12, 1);

    @BeforeEach
    void setUp() {
        loan = LoanAccount.builder()
                .loanId(101L)
                .loanAccountNumber("0012111000000101")
                .customerId(1001L)
                .linkedCasaAccountId(2001L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("12000000.0000"))
                .remainingPrincipal(new BigDecimal("12000000.0000"))
                .loanStatus(LoanStatus.ACTIVE.name())
                .build();

        casaAccount = Account.builder()
                .accountId(2001L)
                .accountNumber("0014210000002001")
                .customerId(1001L)
                .glCode("4211")
                .currency("VND")
                .branchCode("VN001")
                .ledgerBalance(new BigDecimal("10000000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        // Total installment: 1,000,000 principal + 100,000 interest = 1,100,000
        dueSchedule = LoanSchedule.builder()
                .scheduleId(501L)
                .loanId(101L)
                .periodNumber(1)
                .dueDate(workingDate)
                .principalDue(new BigDecimal("1000000.0000"))
                .interestDue(new BigDecimal("100000.0000"))
                .totalInstallment(new BigDecimal("1100000.0000"))
                .principalPaid(BigDecimal.ZERO)
                .interestPaid(BigDecimal.ZERO)
                .penaltyDue(BigDecimal.ZERO)
                .penaltyPaid(BigDecimal.ZERO)
                .status(LoanScheduleStatus.PENDING.name())
                .build();
    }

    @Test
    @DisplayName("Waterfall: Full repayment when CASA balance is sufficient")
    void testFullRepayment() {
        when(loanScheduleRepository.findDueSchedulesOnOrBefore(workingDate)).thenReturn(List.of(dueSchedule));
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(casaAccount));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1111L);

        JournalLeg leg1 = new JournalLeg(1, 1, 2001L, null, "4211", "D",
                new BigDecimal("1000000.0000"), "VND", workingDate, "Thu no goc");
        JournalLeg leg2 = new JournalLeg(1, 2, null, 101L, "2111", "C",
                new BigDecimal("1000000.0000"), "VND", workingDate, "Thu no goc");
        JournalLeg leg3 = new JournalLeg(2, 1, 2001L, null, "4211", "D",
                new BigDecimal("100000.0000"), "VND", workingDate, "Thu no lai");
        JournalLeg leg4 = new JournalLeg(2, 2, null, 101L, "7020", "C",
                new BigDecimal("100000.0000"), "VND", workingDate, "Thu no lai");

        PostingResult postingResult = new PostingResult("LOAN_REPAY", workingDate, "VND",
                new BigDecimal("1100000.0000"), new BigDecimal("1100000.0000"),
                List.of(leg1, leg2, leg3, leg4));

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);
        when(trxJournalActiveRepository.saveAndFlush(any())).thenAnswer(i -> {
            TrxJournalActive j = i.getArgument(0);
            j.setTrxId(999L);
            return j;
        });

        AutoRepaymentBatchResult result = loanRepaymentService.executeAutoRepayment(workingDate);

        assertThat(result.fullyPaidSchedulesCount()).isEqualTo(1);
        assertThat(result.totalPrincipalCollected()).isEqualByComparingTo("1000000.0000");
        assertThat(result.totalInterestCollected()).isEqualByComparingTo("100000.0000");

        // Verify CASA balance reduced by 1,100,000 (10M - 1.1M = 8.9M)
        assertThat(casaAccount.getLedgerBalance()).isEqualByComparingTo("8900000.0000");
        verify(accountRepository).save(casaAccount);

        // Verify Schedule updated to PAID
        assertThat(dueSchedule.getStatus()).isEqualTo(LoanScheduleStatus.PAID.name());
        assertThat(dueSchedule.getPrincipalPaid()).isEqualByComparingTo("1000000.0000");
        assertThat(dueSchedule.getInterestPaid()).isEqualByComparingTo("100000.0000");
        verify(loanScheduleRepository).save(dueSchedule);

        // Verify Loan remaining principal reduced (12M - 1M = 11M)
        assertThat(loan.getRemainingPrincipal()).isEqualByComparingTo("11000000.0000");
        verify(loanAccountRepository).save(loan);
    }

    @Test
    @DisplayName("Waterfall: Partial repayment when CASA balance is insufficient for principal")
    void testPartialRepaymentPriority() {
        // CASA only has 250,000 VND (Enough to pay 100k interest + 150k partial principal)
        casaAccount.setLedgerBalance(new BigDecimal("250000.0000"));

        when(loanScheduleRepository.findDueSchedulesOnOrBefore(workingDate)).thenReturn(List.of(dueSchedule));
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(casaAccount));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1112L);

        JournalLeg leg1 = new JournalLeg(1, 1, 2001L, null, "4211", "D",
                new BigDecimal("150000.0000"), "VND", workingDate, "Thu goc mot phan");
        JournalLeg leg2 = new JournalLeg(1, 2, null, 101L, "2111", "C",
                new BigDecimal("150000.0000"), "VND", workingDate, "Thu goc mot phan");
        JournalLeg leg3 = new JournalLeg(2, 1, 2001L, null, "4211", "D",
                new BigDecimal("100000.0000"), "VND", workingDate, "Thu no lai");
        JournalLeg leg4 = new JournalLeg(2, 2, null, 101L, "7020", "C",
                new BigDecimal("100000.0000"), "VND", workingDate, "Thu no lai");

        PostingResult postingResult = new PostingResult("LOAN_REPAY", workingDate, "VND",
                new BigDecimal("250000.0000"), new BigDecimal("250000.0000"),
                List.of(leg1, leg2, leg3, leg4));

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);
        when(trxJournalActiveRepository.saveAndFlush(any())).thenAnswer(i -> {
            TrxJournalActive j = i.getArgument(0);
            j.setTrxId(999L);
            return j;
        });

        AutoRepaymentBatchResult result = loanRepaymentService.executeAutoRepayment(workingDate);

        assertThat(result.partiallyPaidSchedulesCount()).isEqualTo(1);
        assertThat(result.totalInterestCollected()).isEqualByComparingTo("100000.0000");
        assertThat(result.totalPrincipalCollected()).isEqualByComparingTo("150000.0000");

        // CASA balance drained to 0
        assertThat(casaAccount.getLedgerBalance()).isEqualByComparingTo("0.0000");

        // Schedule marked PARTIALLY_PAID
        assertThat(dueSchedule.getStatus()).isEqualTo(LoanScheduleStatus.PARTIALLY_PAID.name());
        assertThat(dueSchedule.getInterestPaid()).isEqualByComparingTo("100000.0000");
        assertThat(dueSchedule.getPrincipalPaid()).isEqualByComparingTo("150000.0000");

        // Loan remaining principal reduced by 150,000
        assertThat(loan.getRemainingPrincipal()).isEqualByComparingTo("11850000.0000");
    }

    @Test
    @DisplayName("Zero Balance: CASA has 0 VND, schedule is marked OVERDUE if past due")
    void testZeroBalanceMarksOverdue() {
        casaAccount.setLedgerBalance(BigDecimal.ZERO);
        // Due date was yesterday
        dueSchedule.setDueDate(workingDate.minusDays(1));

        when(loanScheduleRepository.findDueSchedulesOnOrBefore(workingDate)).thenReturn(List.of(dueSchedule));
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(casaAccount));

        AutoRepaymentBatchResult result = loanRepaymentService.executeAutoRepayment(workingDate);

        assertThat(result.totalPrincipalCollected()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.totalInterestCollected()).isEqualByComparingTo(BigDecimal.ZERO);

        // Schedule transitioned to OVERDUE
        assertThat(dueSchedule.getStatus()).isEqualTo(LoanScheduleStatus.OVERDUE.name());
        verify(loanScheduleRepository).save(dueSchedule);
        verify(postingEngineService, never()).generateAndValidateEntries(any());
    }
}

package com.cbs.eod.service;

import com.cbs.account.repository.AccountRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.SubledgerGlDiscrepancyException;
import com.cbs.common.exception.TrialBalanceImbalanceException;
import com.cbs.eod.dto.TrialBalanceReport;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class TrialBalanceServiceTest {

    @Mock
    private JournalEntryDoneRepository journalEntryDoneRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private AccountBalanceBucketRepository bucketRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private WorkingDateService workingDateService;

    @InjectMocks
    private TrialBalanceService trialBalanceService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 14);

    @BeforeEach
    void setUp() {
    }

    @Test
    @DisplayName("Trial Balance: Successfully reconciles when all 3 rules pass")
    void testVerifyTrialBalance_Success() {
        // Rule 1: Macro Debit = 10M, Credit = 10M
        when(journalEntryDoneRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("10000000.0000")},
                new Object[]{"C", new BigDecimal("10000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of());

        // Rule 2: Sub-ledger vs GL 4211 Turnover
        when(bucketRepository.sumDailyDebitByWorkingDate(testDate)).thenReturn(new BigDecimal("3000000.0000"));
        when(bucketRepository.sumDailyCreditByWorkingDate(testDate)).thenReturn(new BigDecimal("7000000.0000"));

        when(journalEntryDoneRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("3000000.0000")},
                new Object[]{"C", new BigDecimal("7000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of());

        // Rule 3: Live Account vs Bucket Closing Balance
        when(accountRepository.sumLedgerBalanceByGlCode("4211")).thenReturn(new BigDecimal("15000000.0000"));
        when(bucketRepository.sumClosingBalanceByWorkingDate(testDate)).thenReturn(new BigDecimal("15000000.0000"));

        TrialBalanceReport report = trialBalanceService.verifyTrialBalance(testDate);

        assertThat(report.isBalanced()).isTrue();
        assertThat(report.macroDebitSum()).isEqualByComparingTo("10000000.0000");
        assertThat(report.macroCreditSum()).isEqualByComparingTo("10000000.0000");
        assertThat(report.subledgerDebitSum()).isEqualByComparingTo("3000000.0000");
        assertThat(report.gl4211DebitSum()).isEqualByComparingTo("3000000.0000");
        assertThat(report.subledgerCreditSum()).isEqualByComparingTo("7000000.0000");
        assertThat(report.gl4211CreditSum()).isEqualByComparingTo("7000000.0000");
        assertThat(report.liveAccountTotalBalance()).isEqualByComparingTo("15000000.0000");
        assertThat(report.bucketTotalClosingBalance()).isEqualByComparingTo("15000000.0000");
    }

    @Test
    @DisplayName("Trial Balance: Macro equilibrium failure throws TrialBalanceImbalanceException")
    void testVerifyTrialBalance_MacroImbalanceThrowsException() {
        // Macro Debit = 10M, Credit = 9.995M (5k discrepancy)
        when(journalEntryDoneRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("10000000.0000")},
                new Object[]{"C", new BigDecimal("9995000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of());

        assertThatThrownBy(() -> trialBalanceService.verifyTrialBalance(testDate))
                .isInstanceOf(TrialBalanceImbalanceException.class)
                .hasMessageContaining("General Ledger Macro Equilibrium FAILED");
    }

    @Test
    @DisplayName("Trial Balance: Sub-ledger vs GL turnover mismatch throws SubledgerGlDiscrepancyException")
    void testVerifyTrialBalance_TurnoverMismatchThrowsException() {
        // Rule 1 matches
        when(journalEntryDoneRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("10000000.0000")},
                new Object[]{"C", new BigDecimal("10000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of());

        // Rule 2: Sub-ledger debit is 3M, but GL 4211 debit is 2.5M
        when(bucketRepository.sumDailyDebitByWorkingDate(testDate)).thenReturn(new BigDecimal("3000000.0000"));
        when(bucketRepository.sumDailyCreditByWorkingDate(testDate)).thenReturn(new BigDecimal("7000000.0000"));

        when(journalEntryDoneRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("2500000.0000")},
                new Object[]{"C", new BigDecimal("7000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of());

        assertThatThrownBy(() -> trialBalanceService.verifyTrialBalance(testDate))
                .isInstanceOf(SubledgerGlDiscrepancyException.class)
                .hasMessageContaining("Sub-ledger vs GL Reconciliation FAILED")
                .hasMessageContaining("Debit Turnover");
    }

    @Test
    @DisplayName("Trial Balance: Live account vs bucket closing balance mismatch throws SubledgerGlDiscrepancyException")
    void testVerifyTrialBalance_ClosingBalanceMismatchThrowsException() {
        // Rule 1 matches
        when(journalEntryDoneRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("10000000.0000")},
                new Object[]{"C", new BigDecimal("10000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByDrCr(testDate)).thenReturn(List.of());

        // Rule 2 matches
        when(bucketRepository.sumDailyDebitByWorkingDate(testDate)).thenReturn(new BigDecimal("3000000.0000"));
        when(bucketRepository.sumDailyCreditByWorkingDate(testDate)).thenReturn(new BigDecimal("7000000.0000"));
        when(journalEntryDoneRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of(
                new Object[]{"D", new BigDecimal("3000000.0000")},
                new Object[]{"C", new BigDecimal("7000000.0000")}
        ));
        when(journalEntryActiveRepository.sumTurnoverByGlCodeAndDrCr(testDate, "4211")).thenReturn(List.of());

        // Rule 3: Live Account total is 15M, but Bucket total is 14M
        when(accountRepository.sumLedgerBalanceByGlCode("4211")).thenReturn(new BigDecimal("15000000.0000"));
        when(bucketRepository.sumClosingBalanceByWorkingDate(testDate)).thenReturn(new BigDecimal("14000000.0000"));

        assertThatThrownBy(() -> trialBalanceService.verifyTrialBalance(testDate))
                .isInstanceOf(SubledgerGlDiscrepancyException.class)
                .hasMessageContaining("Sub-ledger vs GL Reconciliation FAILED")
                .hasMessageContaining("Closing Balance");
    }
}

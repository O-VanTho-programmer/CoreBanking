package com.cbs.eod.service;

import com.cbs.account.domain.Account;
import com.cbs.account.domain.DptProductCatalog;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.repository.DptProductCatalogRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateAccrualException;
import com.cbs.eod.dto.AccrualBatchResult;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class InterestAccrualServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DptProductCatalogRepository dptProductCatalogRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private TrxJournalDoneRepository trxJournalDoneRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @InjectMocks
    private InterestAccrualService accrualService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 14);

    private DptProductCatalog standardProduct;

    @BeforeEach
    void setUp() {
        standardProduct = DptProductCatalog.builder()
                .productCode("CASA_VND")
                .productName("Tai khoan thanh toan ca nhan VND")
                .currency("VND")
                .defaultGlCode("4211")
                .interestGlCode("8011")
                .accrualGlCode("4911")
                .annualRate(new BigDecimal("0.0050")) // 0.5% p.a.
                .dayCountBasis("ACT_365")
                .minBalance(new BigDecimal("50000.0000"))
                .isActive("Y")
                .build();
    }

    @Test
    @DisplayName("Pure Formula: Correct calculation with ACT_365")
    void testCalculateDailyInterest_Act365() {
        // 10,000,000 * 0.0050 / 365 = 136.9863013... -> 136.9863
        BigDecimal interest = accrualService.calculateDailyInterest(
                new BigDecimal("10000000.0000"),
                new BigDecimal("0.0050"),
                "ACT_365"
        );
        assertThat(interest).isEqualByComparingTo("136.9863");
    }

    @Test
    @DisplayName("Pure Formula: Correct calculation with ACT_360")
    void testCalculateDailyInterest_Act360() {
        // 10,000,000 * 0.0050 / 360 = 138.888888... -> 138.8889
        BigDecimal interest = accrualService.calculateDailyInterest(
                new BigDecimal("10000000.0000"),
                new BigDecimal("0.0050"),
                "ACT_360"
        );
        assertThat(interest).isEqualByComparingTo("138.8889");
    }

    @Test
    @DisplayName("Pure Formula: Returns zero for non-positive balance or rate")
    void testCalculateDailyInterest_ZeroOrNegative() {
        assertThat(accrualService.calculateDailyInterest(BigDecimal.ZERO, new BigDecimal("0.0050"), "ACT_365"))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(accrualService.calculateDailyInterest(new BigDecimal("-100000"), new BigDecimal("0.0050"), "ACT_365"))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(accrualService.calculateDailyInterest(new BigDecimal("1000000"), BigDecimal.ZERO, "ACT_365"))
                .isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(accrualService.calculateDailyInterest(null, new BigDecimal("0.0050"), "ACT_365"))
                .isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    @DisplayName("Accrual Batch: Successfully calculates interest and posts balanced journal entry")
    void testExecuteDailyAccrual_Success() {
        Account account1 = Account.builder()
                .accountId(101L)
                .accountNumber("101001")
                .productCode("CASA_VND")
                .ledgerBalance(new BigDecimal("10000000.0000"))
                .accruedInterest(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        Account account2 = Account.builder()
                .accountId(102L)
                .accountNumber("101002")
                .productCode("CASA_VND")
                .ledgerBalance(new BigDecimal("20000000.0000"))
                .accruedInterest(new BigDecimal("50.0000"))
                .status("ACTIVE")
                .build();

        when(trxJournalActiveRepository.existsByChannelIdAndIdempotencyKey("BATCH", "ACCRUAL:" + testDate))
                .thenReturn(false);
        when(accountRepository.findByStatus("ACTIVE")).thenReturn(List.of(account1, account2));
        when(dptProductCatalogRepository.findById("CASA_VND")).thenReturn(Optional.of(standardProduct));

        // Account 1: 10M * 0.005 / 365 = 136.9863
        // Account 2: 20M * 0.005 / 365 = 273.9726
        // Total = 410.9589
        BigDecimal expectedTotal = new BigDecimal("410.9589");

        List<JournalLeg> legs = List.of(
                new JournalLeg(1, 1, null, "8011", "D", expectedTotal, "VND", testDate, "DR"),
                new JournalLeg(1, 2, null, "4911", "C", expectedTotal, "VND", testDate, "CR")
        );
        PostingResult postingResult = new PostingResult("EOD_ACCRUAL", testDate, "VND", expectedTotal, expectedTotal, legs);

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class)))
                .thenReturn(postingResult);

        TrxJournalActive savedJournal = TrxJournalActive.builder()
                .trxId(999L)
                .trxNumber("ACCRUAL-20261014-12345")
                .build();
        when(trxJournalActiveRepository.saveAndFlush(any(TrxJournalActive.class)))
                .thenReturn(savedJournal);

        AccrualBatchResult result = accrualService.executeDailyAccrual(testDate);

        assertThat(result.workingDate()).isEqualTo(testDate);
        assertThat(result.totalAccountsProcessed()).isEqualTo(2);
        assertThat(result.totalAccountsAccrued()).isEqualTo(2);
        assertThat(result.totalInterestAccrued()).isEqualByComparingTo(expectedTotal);
        assertThat(result.journalTrxId()).isEqualTo(999L);

        // Verify account mutations
        assertThat(account1.getAccruedInterest()).isEqualByComparingTo("136.9863");
        assertThat(account2.getAccruedInterest()).isEqualByComparingTo(new BigDecimal("50.0000").add(new BigDecimal("273.9726")));
        verify(accountRepository, times(2)).save(any(Account.class));

        // Verify posting request was submitted for EOD_ACCRUAL
        ArgumentCaptor<PostingRequest> requestCaptor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngineService).generateAndValidateEntries(requestCaptor.capture());
        assertThat(requestCaptor.getValue().trxCode()).isEqualTo("EOD_ACCRUAL");
        assertThat(requestCaptor.getValue().trxAmount()).isEqualByComparingTo(expectedTotal);

        // Verify 2 journal entry legs persisted
        verify(journalEntryActiveRepository, times(2)).save(any(JournalEntryActive.class));
    }

    @Test
    @DisplayName("Accrual Batch: Account below minimum balance is skipped")
    void testExecuteDailyAccrual_BelowMinBalanceSkipped() {
        Account accountBelowMin = Account.builder()
                .accountId(103L)
                .accountNumber("101003")
                .productCode("CASA_VND")
                .ledgerBalance(new BigDecimal("30000.0000")) // < 50,000 minBalance
                .accruedInterest(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();

        when(trxJournalActiveRepository.existsByChannelIdAndIdempotencyKey("BATCH", "ACCRUAL:" + testDate))
                .thenReturn(false);
        when(accountRepository.findByStatus("ACTIVE")).thenReturn(List.of(accountBelowMin));
        when(dptProductCatalogRepository.findById("CASA_VND")).thenReturn(Optional.of(standardProduct));

        AccrualBatchResult result = accrualService.executeDailyAccrual(testDate);

        assertThat(result.totalAccountsProcessed()).isEqualTo(1);
        assertThat(result.totalAccountsAccrued()).isEqualTo(0);
        assertThat(result.totalInterestAccrued()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(result.journalTrxId()).isNull();

        verify(accountRepository, never()).save(any(Account.class));
        verify(postingEngineService, never()).generateAndValidateEntries(any());
        verify(trxJournalActiveRepository, never()).saveAndFlush(any());
    }

    @Test
    @DisplayName("Accrual Batch: Duplicate run on same working date throws DuplicateAccrualException")
    void testExecuteDailyAccrual_DuplicateThrowsException() {
        when(trxJournalActiveRepository.existsByChannelIdAndIdempotencyKey("BATCH", "ACCRUAL:" + testDate))
                .thenReturn(true);

        assertThatThrownBy(() -> accrualService.executeDailyAccrual(testDate))
                .isInstanceOf(DuplicateAccrualException.class)
                .hasMessageContaining("Daily interest accrual has already been executed for working date: 2026-10-14");

        verify(accountRepository, never()).findByStatus(any());
    }
}

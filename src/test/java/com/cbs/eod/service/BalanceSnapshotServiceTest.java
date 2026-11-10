package com.cbs.eod.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.DptProductCatalog;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.repository.DptProductCatalogRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateSnapshotException;
import com.cbs.eod.domain.AccountBalanceBucket;
import com.cbs.eod.dto.BalanceSnapshotBatchResult;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BalanceSnapshotServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private DptProductCatalogRepository dptProductCatalogRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private JournalEntryDoneRepository journalEntryDoneRepository;

    @Mock
    private AccountBalanceBucketRepository bucketRepository;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private InterestAccrualService interestAccrualService;

    @InjectMocks
    private BalanceSnapshotService balanceSnapshotService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 14);
    private DptProductCatalog product;

    @BeforeEach
    void setUp() {
        product = DptProductCatalog.builder()
                .productCode("CASA_VND")
                .annualRate(new BigDecimal("0.0050"))
                .dayCountBasis("ACT_365")
                .minBalance(new BigDecimal("50000.0000"))
                .isActive("Y")
                .build();
    }

    @Test
    @DisplayName("Snapshot Batch: Successfully freezes closing balances, turnovers, and accrued interest")
    void testTakeDailySnapshot_Success() {
        Account account1 = Account.builder()
                .accountId(101L)
                .accountNumber("ACCT001")
                .productCode("CASA_VND")
                .ledgerBalance(new BigDecimal("5000000.0000"))
                .build();

        Account account2 = Account.builder()
                .accountId(102L)
                .accountNumber("ACCT002")
                .productCode("CASA_VND")
                .ledgerBalance(new BigDecimal("10000000.0000"))
                .build();

        when(bucketRepository.existsByWorkingDate(testDate)).thenReturn(false);
        when(accountRepository.findAll()).thenReturn(List.of(account1, account2));
        when(dptProductCatalogRepository.findById("CASA_VND")).thenReturn(Optional.of(product));

        // Mock aggregated turnover: Account 101 has Debit 1M, Credit 3M; Account 102 has 0
        List<Object[]> rawTurnovers = List.of(
                new Object[]{101L, "D", new BigDecimal("1000000.0000")},
                new Object[]{101L, "C", new BigDecimal("3000000.0000")}
        );
        when(journalEntryActiveRepository.aggregateDailyTurnoverByValueDate(testDate)).thenReturn(rawTurnovers);

        when(interestAccrualService.calculateDailyInterest(new BigDecimal("5000000.0000"), new BigDecimal("0.0050"), "ACT_365"))
                .thenReturn(new BigDecimal("68.4932"));
        when(interestAccrualService.calculateDailyInterest(new BigDecimal("10000000.0000"), new BigDecimal("0.0050"), "ACT_365"))
                .thenReturn(new BigDecimal("136.9863"));

        BalanceSnapshotBatchResult result = balanceSnapshotService.takeDailySnapshot(testDate);

        assertThat(result.workingDate()).isEqualTo(testDate);
        assertThat(result.totalAccountsSnapshotted()).isEqualTo(2);
        assertThat(result.totalClosingBalance()).isEqualByComparingTo("15000000.0000");
        assertThat(result.totalDailyDebitSum()).isEqualByComparingTo("1000000.0000");
        assertThat(result.totalDailyCreditSum()).isEqualByComparingTo("3000000.0000");
        assertThat(result.totalDailyAccruedInterest()).isEqualByComparingTo("205.4795");

        // Verify batch save arguments
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<AccountBalanceBucket>> captor = ArgumentCaptor.forClass(List.class);
        verify(bucketRepository).saveAll(captor.capture());

        List<AccountBalanceBucket> savedBuckets = captor.getValue();
        assertThat(savedBuckets).hasSize(2);

        AccountBalanceBucket bucket1 = savedBuckets.stream()
                .filter(b -> b.getId().getAccountId().equals(101L))
                .findFirst().orElseThrow();
        assertThat(bucket1.getId().getWorkingDate()).isEqualTo(testDate);
        assertThat(bucket1.getClosingBalance()).isEqualByComparingTo("5000000.0000");
        assertThat(bucket1.getDailyDebitSum()).isEqualByComparingTo("1000000.0000");
        assertThat(bucket1.getDailyCreditSum()).isEqualByComparingTo("3000000.0000");
        assertThat(bucket1.getAccruedInterestDay()).isEqualByComparingTo("68.4932");

        AccountBalanceBucket bucket2 = savedBuckets.stream()
                .filter(b -> b.getId().getAccountId().equals(102L))
                .findFirst().orElseThrow();
        assertThat(bucket2.getId().getWorkingDate()).isEqualTo(testDate);
        assertThat(bucket2.getClosingBalance()).isEqualByComparingTo("10000000.0000");
        assertThat(bucket2.getDailyDebitSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bucket2.getDailyCreditSum()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(bucket2.getAccruedInterestDay()).isEqualByComparingTo("136.9863");
    }

    @Test
    @DisplayName("Snapshot Batch: Duplicate run on same working date throws DuplicateSnapshotException")
    void testTakeDailySnapshot_DuplicateThrowsException() {
        when(bucketRepository.existsByWorkingDate(testDate)).thenReturn(true);

        assertThatThrownBy(() -> balanceSnapshotService.takeDailySnapshot(testDate))
                .isInstanceOf(DuplicateSnapshotException.class)
                .hasMessageContaining("Balance snapshot bucket has already been created for working date: 2026-10-14");

        verify(bucketRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Snapshot Batch: Falls back to current working date when argument is null")
    void testTakeDailySnapshot_NullWorkingDateFallback() {
        when(workingDateService.getCurrentWorkingDate()).thenReturn(testDate);
        when(bucketRepository.existsByWorkingDate(testDate)).thenReturn(false);
        when(journalEntryActiveRepository.aggregateDailyTurnoverByValueDate(testDate)).thenReturn(List.of());
        when(accountRepository.findAll()).thenReturn(List.of());

        BalanceSnapshotBatchResult result = balanceSnapshotService.takeDailySnapshot(null);

        assertThat(result.workingDate()).isEqualTo(testDate);
        verify(workingDateService).getCurrentWorkingDate();
    }
}

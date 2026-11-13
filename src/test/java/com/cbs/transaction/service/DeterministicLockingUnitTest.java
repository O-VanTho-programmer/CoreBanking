package com.cbs.transaction.service;

import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.deposit.domain.Account;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.ReverseCommand;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.dto.WithdrawCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit test suite specifically validating Invariant 3:
 * Deterministic Pessimistic Row Locking and Deadlock Prevention.
 *
 * Guarantees that any multi-account transaction always acquires row locks
 * in strictly ascending account_id order (ORDER BY account_id ASC FOR UPDATE),
 * completely eliminating cyclic wait deadlock conditions between concurrent opposing transfers.
 */
@ExtendWith(MockitoExtension.class)
class DeterministicLockingUnitTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TransactionService transactionService;

    @Captor
    private ArgumentCaptor<List<Long>> accountIdsLockCaptor;

    private final LocalDate workingDate = LocalDate.of(2026, 11, 13);

    @BeforeEach
    void setUp() {
        lenient().when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        lenient().when(workingDateService.isSystemCutoff()).thenReturn(false);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(1001L);
        lenient().when(idempotencyService.acquireLock(any(), any())).thenReturn(true);
        lenient().when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());

        TrxJournalActive mockJournal = TrxJournalActive.builder()
                .trxId(999L)
                .trxNumber("TRX-TEST-001")
                .status("IN_FLIGHT")
                .createdAt(OffsetDateTime.now())
                .build();
        lenient().when(trxJournalActiveRepository.saveAndFlush(any(TrxJournalActive.class))).thenReturn(mockJournal);
    }

    @Test
    @DisplayName("Invariant 3: Transfer A -> B where id(A) > id(B) locks [B, A] in ascending order")
    void shouldLockAccountsInAscendingOrderWhenFromIdGreaterThanToId() {
        // From account has ID 200, To account has ID 50
        when(accountRepository.findIdByAccountNumber("ACC_FROM_200")).thenReturn(Optional.of(200L));
        when(accountRepository.findIdByAccountNumber("ACC_TO_050")).thenReturn(Optional.of(50L));

        Account acc50 = createActiveAccount(50L, "ACC_TO_050", new BigDecimal("1000000.0000"));
        Account acc200 = createActiveAccount(200L, "ACC_FROM_200", new BigDecimal("5000000.0000"));

        when(accountRepository.findAndLockAccountsByIds(accountIdsLockCaptor.capture()))
                .thenReturn(List.of(acc50, acc200));

        stubSuccessfulPosting("TRF_INTERNAL", 200L, 50L, new BigDecimal("1000000.0000"));

        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-lock-01")
                .fromAccountNumber("ACC_FROM_200")
                .toAccountNumber("ACC_TO_050")
                .amount(new BigDecimal("1000000.0000"))
                .currency("VND")
                .build();

        transactionService.transfer(cmd);

        // Verify lock acquisition order is strictly [50L, 200L] (ascending)
        List<Long> lockedIds = accountIdsLockCaptor.getValue();
        assertThat(lockedIds)
                .containsExactly(50L, 200L)
                .isSorted();
    }

    @Test
    @DisplayName("Invariant 3: Transfer A -> B where id(A) < id(B) locks [A, B] in ascending order")
    void shouldLockAccountsInAscendingOrderWhenFromIdLessThanToId() {
        // From account has ID 30, To account has ID 90
        when(accountRepository.findIdByAccountNumber("ACC_FROM_030")).thenReturn(Optional.of(30L));
        when(accountRepository.findIdByAccountNumber("ACC_TO_090")).thenReturn(Optional.of(90L));

        Account acc30 = createActiveAccount(30L, "ACC_FROM_030", new BigDecimal("5000000.0000"));
        Account acc90 = createActiveAccount(90L, "ACC_TO_090", new BigDecimal("1000000.0000"));

        when(accountRepository.findAndLockAccountsByIds(accountIdsLockCaptor.capture()))
                .thenReturn(List.of(acc30, acc90));

        stubSuccessfulPosting("TRF_INTERNAL", 30L, 90L, new BigDecimal("500000.0000"));

        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-lock-02")
                .fromAccountNumber("ACC_FROM_030")
                .toAccountNumber("ACC_TO_090")
                .amount(new BigDecimal("500000.0000"))
                .currency("VND")
                .build();

        transactionService.transfer(cmd);

        // Verify lock acquisition order is strictly [30L, 90L] (ascending)
        List<Long> lockedIds = accountIdsLockCaptor.getValue();
        assertThat(lockedIds)
                .containsExactly(30L, 90L)
                .isSorted();
    }

    @Test
    @DisplayName("Invariant 3: Self-transfer with identical accounts is rejected without DB lock")
    void shouldRejectSelfTransferBeforeAcquiringLocks() {
        when(accountRepository.findIdByAccountNumber("ACC_SAME")).thenReturn(Optional.of(77L));

        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-lock-self")
                .fromAccountNumber("ACC_SAME")
                .toAccountNumber("ACC_SAME")
                .amount(new BigDecimal("100000.0000"))
                .build();

        assertThatThrownBy(() -> transactionService.transfer(cmd))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot be identical");

        // Verify DB locking was NEVER invoked
        verify(accountRepository, never()).findAndLockAccountsByIds(any());
        verify(accountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Invariant 3: Single-account deposit acquires exclusive write lock on target account")
    void shouldAcquirePessimisticWriteLockOnDeposit() {
        when(accountRepository.findIdByAccountNumber("ACC_DEP_88")).thenReturn(Optional.of(88L));
        Account acc = createActiveAccount(88L, "ACC_DEP_88", new BigDecimal("2000000.0000"));
        when(accountRepository.findByIdForUpdate(88L)).thenReturn(Optional.of(acc));

        stubSuccessfulSingleLegPosting("CASH_DEP", 88L, "C", new BigDecimal("1000000.0000"));

        DepositCommand cmd = DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("idemp-dep-lock")
                .accountNumber("ACC_DEP_88")
                .amount(new BigDecimal("1000000.0000"))
                .build();

        transactionService.deposit(cmd);

        verify(accountRepository).findByIdForUpdate(88L);
        assertThat(acc.getLedgerBalance()).isEqualByComparingTo("3000000.0000");
    }

    @Test
    @DisplayName("Invariant 3: Single-account withdraw acquires exclusive write lock on source account")
    void shouldAcquirePessimisticWriteLockOnWithdrawal() {
        when(accountRepository.findIdByAccountNumber("ACC_WDR_99")).thenReturn(Optional.of(99L));
        Account acc = createActiveAccount(99L, "ACC_WDR_99", new BigDecimal("5000000.0000"));
        when(accountRepository.findByIdForUpdate(99L)).thenReturn(Optional.of(acc));

        stubSuccessfulSingleLegPosting("CASH_WDR", 99L, "D", new BigDecimal("1500000.0000"));

        WithdrawCommand cmd = WithdrawCommand.builder()
                .channelId("ATM")
                .idempotencyKey("idemp-wdr-lock")
                .accountNumber("ACC_WDR_99")
                .amount(new BigDecimal("1500000.0000"))
                .build();

        transactionService.withdraw(cmd);

        verify(accountRepository).findByIdForUpdate(99L);
        assertThat(acc.getLedgerBalance()).isEqualByComparingTo("3500000.0000");
    }

    @Test
    @DisplayName("Invariant 3: Multi-leg reversal deduplicates and sorts account IDs before locking")
    void shouldDeduplicateAndSortAccountIdsWhenReversing() {
        TrxJournalActive origTrx = TrxJournalActive.builder()
                .trxId(999L)
                .trxNumber("TRX-ORIG-999")
                .channelId("MB")
                .idempotencyKey("orig-key")
                .trxCode("TRF_INTERNAL")
                .status("COMPLETED")
                .isReversed("N")
                .workingDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .build();

        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-ORIG-999"))
                .thenReturn(Optional.of(origTrx));

        // Original transaction legs have unordered, duplicated account IDs: 40L, 10L, 40L
        JournalEntryActive leg1 = JournalEntryActive.builder()
                .entryId(1L).trxId(999L).entryGroup(1).entryIndex(1).accountId(40L).glCode("4211").drCr("D")
                .amount(new BigDecimal("1000000.0000")).currency("VND").valueDate(workingDate).build();
        JournalEntryActive leg2 = JournalEntryActive.builder()
                .entryId(2L).trxId(999L).entryGroup(1).entryIndex(2).accountId(10L).glCode("4211").drCr("C")
                .amount(new BigDecimal("1000000.0000")).currency("VND").valueDate(workingDate).build();
        JournalEntryActive leg3 = JournalEntryActive.builder()
                .entryId(3L).trxId(999L).entryGroup(2).entryIndex(1).accountId(40L).glCode("4211").drCr("D")
                .amount(new BigDecimal("11000.0000")).currency("VND").valueDate(workingDate).build();
        JournalEntryActive leg4 = JournalEntryActive.builder()
                .entryId(4L).trxId(999L).entryGroup(2).entryIndex(2).accountId(null).glCode("7111").drCr("C")
                .amount(new BigDecimal("11000.0000")).currency("VND").valueDate(workingDate).build();

        when(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(999L))
                .thenReturn(List.of(leg1, leg2, leg3, leg4));

        Account acc10 = createActiveAccount(10L, "ACC_10", new BigDecimal("5000000.0000"));
        Account acc40 = createActiveAccount(40L, "ACC_40", new BigDecimal("5000000.0000"));

        when(accountRepository.findAndLockAccountsByIds(accountIdsLockCaptor.capture()))
                .thenReturn(List.of(acc10, acc40));

        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey("idemp-rev-lock")
                .originalTrxNumber("TRX-ORIG-999")
                .reason("Administrative correction")
                .makerId("supervisor01")
                .build();

        transactionService.reverse(cmd);

        // Verify locked account IDs are strictly deduplicated and sorted [10L, 40L]
        List<Long> lockedIds = accountIdsLockCaptor.getValue();
        assertThat(lockedIds)
                .containsExactly(10L, 40L)
                .isSorted();
    }

    @Test
    @DisplayName("Distributed Locking: Fast rejection when idempotency lock acquisition fails")
    void shouldRejectEarlyWhenIdempotencyLockFails() {
        when(idempotencyService.acquireLock("MB", "concurrent-key")).thenReturn(false);

        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("concurrent-key")
                .fromAccountNumber("ACC_1")
                .toAccountNumber("ACC_2")
                .amount(new BigDecimal("100000.0000"))
                .build();

        assertThatThrownBy(() -> transactionService.transfer(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("IN_FLIGHT");

        // Verify no account lookups or row locks were attempted
        verifyNoInteractions(accountRepository);
    }

    // Helper methods
    private Account createActiveAccount(Long accountId, String accountNumber, BigDecimal ledgerBalance) {
        return Account.builder()
                .accountId(accountId)
                .accountNumber(accountNumber)
                .status("ACTIVE")
                .ledgerBalance(ledgerBalance)
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();
    }

    private void stubSuccessfulPosting(String trxCode, Long fromAccId, Long toAccId, BigDecimal amount) {
        PostingResult postingResult = new PostingResult(
                trxCode,
                workingDate,
                "VND",
                amount,
                amount,
                List.of(
                        new JournalLeg(1, 1, fromAccId, "4211", "D", amount, "VND", workingDate, "Transfer out"),
                        new JournalLeg(1, 2, toAccId, "4211", "C", amount, "VND", workingDate, "Transfer in")
                )
        );
        lenient().when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class)))
                .thenReturn(postingResult);
    }

    private void stubSuccessfulSingleLegPosting(String trxCode, Long accId, String drCr, BigDecimal amount) {
        PostingResult postingResult = new PostingResult(
                trxCode,
                workingDate,
                "VND",
                amount,
                amount,
                List.of(
                        new JournalLeg(1, 1, accId, "4211", drCr, amount, "VND", workingDate, "Single leg"),
                        new JournalLeg(1, 2, null, "1011", "D".equals(drCr) ? "C" : "D", amount, "VND", workingDate, "Vault leg")
                )
        );
        lenient().when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class)))
                .thenReturn(postingResult);
    }
}

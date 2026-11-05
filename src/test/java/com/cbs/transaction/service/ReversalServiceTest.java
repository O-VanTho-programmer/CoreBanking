package com.cbs.transaction.service;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.*;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.ReverseCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@ExtendWith(MockitoExtension.class)
class ReversalServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TransactionService transactionService;

    private final LocalDate workingDate = LocalDate.of(2026, 10, 26);

    @BeforeEach
    void setUp() {
        lenient().when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        lenient().when(workingDateService.isSystemCutoff()).thenReturn(false);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(99999L);
        lenient().when(idempotencyService.acquireLock(any(), any())).thenReturn(true);
    }

    @Test
    @DisplayName("Reversal: Idempotent replay when reversal request already processed")
    void shouldReplayExistingReversalReceiptWhenIdempotent() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-001")
                .originalTrxNumber("TRX-20261026-00000001")
                .reason("Customer Dispute")
                .makerId("teller01")
                .build();

        TransactionReceipt existingReceipt = TransactionReceipt.builder()
                .trxId(999L)
                .trxNumber("TRX-20261026-00099999")
                .status("COMPLETED")
                .originalTrxId(100L)
                .originalTrxNumber("TRX-20261026-00000001")
                .reversalReason("Customer Dispute")
                .build();

        when(idempotencyService.checkExisting("MB", "idemp-rev-001")).thenReturn(Optional.of(existingReceipt));

        TransactionReceipt result = transactionService.reverse(cmd);

        assertThat(result).isSameAs(existingReceipt);
        verifyNoInteractions(trxJournalActiveRepository);
        verifyNoInteractions(accountRepository);
    }

    @Test
    @DisplayName("Reversal: Rejected when system is in Cutoff status (Invariant 4)")
    void shouldThrowExceptionWhenReversalDuringCutoff() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-002")
                .originalTrxNumber("TRX-20261026-00000001")
                .reason("Teller correction")
                .makerId("teller01")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(true);

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cutoff");
    }

    @Test
    @DisplayName("Reversal: Throws TransactionNotFoundException when original transaction does not exist")
    void shouldThrowExceptionWhenOriginalNotFound() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-003")
                .originalTrxNumber("TRX-NON-EXISTENT")
                .reason("Dispute")
                .makerId("teller01")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-NON-EXISTENT")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(TransactionNotFoundException.class)
                .hasMessageContaining("TRX-NON-EXISTENT");
    }

    @Test
    @DisplayName("Reversal: Throws IllegalStateException when original transaction is not COMPLETED")
    void shouldThrowExceptionWhenOriginalNotCompleted() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-004")
                .originalTrxNumber("TRX-20261026-00000002")
                .reason("Dispute")
                .makerId("teller01")
                .build();

        TrxJournalActive inFlightTrx = TrxJournalActive.builder()
                .trxId(102L)
                .trxNumber("TRX-20261026-00000002")
                .status("IN_FLIGHT")
                .workingDate(workingDate)
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261026-00000002")).thenReturn(Optional.of(inFlightTrx));

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Only COMPLETED transactions can be reversed");
    }

    @Test
    @DisplayName("Reversal: Double-reversal prevented by throwing TransactionAlreadyReversedException")
    void shouldThrowExceptionWhenOriginalAlreadyReversed() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-005")
                .originalTrxNumber("TRX-20261026-00000003")
                .reason("Dispute")
                .makerId("teller01")
                .build();

        TrxJournalActive alreadyReversed = TrxJournalActive.builder()
                .trxId(103L)
                .trxNumber("TRX-20261026-00000003")
                .status("COMPLETED")
                .isReversed("Y")
                .workingDate(workingDate)
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261026-00000003")).thenReturn(Optional.of(alreadyReversed));

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(TransactionAlreadyReversedException.class)
                .hasMessageContaining("TRX-20261026-00000003");
    }

    @Test
    @DisplayName("Reversal: Cannot reverse a reversal transaction itself (throws CannotReverseReversalException)")
    void shouldThrowExceptionWhenOriginalIsItselfReversal() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-006")
                .originalTrxNumber("TRX-20261026-00000004")
                .reason("Dispute")
                .makerId("teller01")
                .build();

        TrxJournalActive reversalTrx = TrxJournalActive.builder()
                .trxId(104L)
                .trxNumber("TRX-20261026-00000004")
                .status("COMPLETED")
                .isReversed("N")
                .originalTrxId(99L) // points to earlier trx
                .workingDate(workingDate)
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261026-00000004")).thenReturn(Optional.of(reversalTrx));

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(CannotReverseReversalException.class)
                .hasMessageContaining("TRX-20261026-00000004");
    }

    @Test
    @DisplayName("Reversal: Throws ReversalWorkingDateMismatchException when transaction date does not match current date")
    void shouldThrowExceptionWhenWorkingDateMismatch() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-007")
                .originalTrxNumber("TRX-20261020-00000005")
                .reason("Dispute")
                .makerId("teller01")
                .build();

        TrxJournalActive pastDateTrx = TrxJournalActive.builder()
                .trxId(105L)
                .trxNumber("TRX-20261020-00000005")
                .status("COMPLETED")
                .isReversed("N")
                .workingDate(LocalDate.of(2026, 10, 20))
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261020-00000005")).thenReturn(Optional.of(pastDateTrx));

        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(ReversalWorkingDateMismatchException.class)
                .hasMessageContaining("2026-10-20");
    }

    @Test
    @DisplayName("Reversal: Throws InsufficientFundsException when recipient account has spent the funds (Invariant 2)")
    void shouldThrowExceptionWhenCreditedAccountHasInsufficientFunds() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-rev-008")
                .originalTrxNumber("TRX-20261026-00000006")
                .reason("Transfer Error")
                .makerId("teller01")
                .build();

        TrxJournalActive origTrx = TrxJournalActive.builder()
                .trxId(106L)
                .trxNumber("TRX-20261026-00000006")
                .trxCode("TRF_INTERNAL")
                .status("COMPLETED")
                .isReversed("N")
                .workingDate(workingDate)
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261026-00000006")).thenReturn(Optional.of(origTrx));

        // Original legs: Acc 1 (DR 1,000,000), Acc 2 (CR 1,000,000)
        List<JournalEntryActive> origLegs = List.of(
                JournalEntryActive.builder().entryId(1L).trxId(106L).entryGroup(1).entryIndex(1).accountId(1L).glCode("4211").drCr("D").amount(new BigDecimal("1000000.0000")).currency("VND").valueDate(workingDate).build(),
                JournalEntryActive.builder().entryId(2L).trxId(106L).entryGroup(1).entryIndex(2).accountId(2L).glCode("4211").drCr("C").amount(new BigDecimal("1000000.0000")).currency("VND").valueDate(workingDate).build()
        );
        when(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(106L)).thenReturn(origLegs);

        // Acc 2 only has 200,000 available (spent 800,000)
        Account acc1 = Account.builder().accountId(1L).accountNumber("1000000000000001").status("ACTIVE").ledgerBalance(new BigDecimal("500000.0000")).holdBalance(BigDecimal.ZERO).currency("VND").build();
        Account acc2 = Account.builder().accountId(2L).accountNumber("1000000000000002").status("ACTIVE").ledgerBalance(new BigDecimal("200000.0000")).holdBalance(BigDecimal.ZERO).currency("VND").build();

        when(accountRepository.findAndLockAccountsByIds(List.of(1L, 2L))).thenReturn(List.of(acc1, acc2));

        // Reversal inverts Acc 2 to Debit 1,000,000. Acc 2 only has 200,000 -> InsufficientFundsException!
        assertThatThrownBy(() -> transactionService.reverse(cmd))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Reversal: Successful reversal inverts legs, restores balances, and creates audit lineage")
    void shouldExecuteReversalSuccessfully() {
        ReverseCommand cmd = ReverseCommand.builder()
                .channelId("BR")
                .idempotencyKey("idemp-rev-009")
                .originalTrxNumber("TRX-20261026-00000007")
                .reason("Mistaken Recipient")
                .makerId("teller01")
                .build();

        TrxJournalActive origTrx = TrxJournalActive.builder()
                .trxId(107L)
                .trxNumber("TRX-20261026-00000007")
                .trxCode("TRF_INTERNAL")
                .status("COMPLETED")
                .isReversed("N")
                .workingDate(workingDate)
                .createdAt(OffsetDateTime.now().minusHours(1))
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(trxJournalActiveRepository.findByTrxNumberForUpdate("TRX-20261026-00000007")).thenReturn(Optional.of(origTrx));

        // Original legs: Principal 2M (Acc 1 D, Acc 2 C) + Fee 5k (Acc 1 D, GL 7021 C)
        List<JournalEntryActive> origLegs = List.of(
                JournalEntryActive.builder().entryId(1L).trxId(107L).entryGroup(1).entryIndex(1).accountId(1L).glCode("4211").drCr("D").amount(new BigDecimal("2000000.0000")).currency("VND").valueDate(workingDate).build(),
                JournalEntryActive.builder().entryId(2L).trxId(107L).entryGroup(1).entryIndex(2).accountId(2L).glCode("4211").drCr("C").amount(new BigDecimal("2000000.0000")).currency("VND").valueDate(workingDate).build(),
                JournalEntryActive.builder().entryId(3L).trxId(107L).entryGroup(2).entryIndex(1).accountId(1L).glCode("4211").drCr("D").amount(new BigDecimal("5000.0000")).currency("VND").valueDate(workingDate).build(),
                JournalEntryActive.builder().entryId(4L).trxId(107L).entryGroup(2).entryIndex(2).accountId(null).glCode("7021").drCr("C").amount(new BigDecimal("5000.0000")).currency("VND").valueDate(workingDate).build()
        );
        when(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(107L)).thenReturn(origLegs);

        // Account states before reversal:
        // Acc 1: had transferred 2M + 5k fee -> current balance = 3,000,000
        Account acc1 = Account.builder().accountId(1L).accountNumber("1000000000000001").status("ACTIVE").ledgerBalance(new BigDecimal("3000000.0000")).holdBalance(BigDecimal.ZERO).currency("VND").build();
        // Acc 2: had received 2M -> current balance = 2,500,000
        Account acc2 = Account.builder().accountId(2L).accountNumber("1000000000000002").status("ACTIVE").ledgerBalance(new BigDecimal("2500000.0000")).holdBalance(BigDecimal.ZERO).currency("VND").build();

        // Lock ordering MUST be [1L, 2L]
        when(accountRepository.findAndLockAccountsByIds(List.of(1L, 2L))).thenReturn(List.of(acc1, acc2));

        TrxJournalActive reversalInFlight = TrxJournalActive.builder()
                .trxId(888L)
                .trxNumber("TRX-20261026-00099999")
                .status("IN_FLIGHT")
                .createdAt(OffsetDateTime.now())
                .build();
        when(trxJournalActiveRepository.saveAndFlush(any(TrxJournalActive.class))).thenReturn(reversalInFlight);

        TransactionReceipt receipt = transactionService.reverse(cmd);

        assertThat(receipt).isNotNull();
        assertThat(receipt.status()).isEqualTo("COMPLETED");
        assertThat(receipt.trxNumber()).isEqualTo("TRX-20261026-00099999");
        assertThat(receipt.originalTrxId()).isEqualTo(107L);
        assertThat(receipt.originalTrxNumber()).isEqualTo("TRX-20261026-00000007");
        assertThat(receipt.reversalReason()).isEqualTo("Mistaken Recipient");

        // Balance verifications:
        // Acc 1 gets back 2,000,000 + 5,000 = 5,005,000
        assertThat(acc1.getLedgerBalance()).isEqualByComparingTo("5005000.0000");
        // Acc 2 has 2,000,000 debited back: 2,500,000 - 2,000,000 = 500,000
        assertThat(acc2.getLedgerBalance()).isEqualByComparingTo("500000.0000");

        // Original transaction must be marked isReversed = 'Y'
        assertThat(origTrx.getIsReversed()).isEqualTo("Y");
        verify(trxJournalActiveRepository).save(origTrx);

        // Reversal header must be updated to COMPLETED
        assertThat(reversalInFlight.getStatus()).isEqualTo("COMPLETED");
        verify(trxJournalActiveRepository).save(reversalInFlight);

        // 4 inverted journal legs saved
        verify(journalEntryActiveRepository, times(4)).save(any(JournalEntryActive.class));

        // Idempotency cached
        verify(idempotencyService).recordCompletion(eq("BR"), eq("idemp-rev-009"), any(TransactionReceipt.class));
    }
}

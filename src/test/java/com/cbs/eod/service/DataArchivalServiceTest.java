package com.cbs.eod.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.InFlightTransactionFoundException;
import com.cbs.eod.dto.ArchivalBatchResult;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.JournalEntryDone;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.domain.TrxJournalDone;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DataArchivalServiceTest {

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private TrxJournalDoneRepository trxJournalDoneRepository;

    @Mock
    private JournalEntryDoneRepository journalEntryDoneRepository;

    @Mock
    private WorkingDateService workingDateService;

    @InjectMocks
    private DataArchivalService dataArchivalService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 14);

    @Test
    @DisplayName("Archival: Throws InFlightTransactionFoundException when IN_FLIGHT transactions exist")
    void testArchiveCompletedTransactions_InFlightThrowsException() {
        when(trxJournalActiveRepository.countInFlightOnOrBefore(testDate)).thenReturn(2L);

        assertThatThrownBy(() -> dataArchivalService.archiveCompletedTransactions(testDate))
                .isInstanceOf(InFlightTransactionFoundException.class)
                .hasMessageContaining("Found 2 IN_FLIGHT transactions");

        verify(trxJournalActiveRepository, never()).findEligibleForArchival(any());
        verify(trxJournalDoneRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Archival: Returns 0 counts when no eligible transactions exist")
    void testArchiveCompletedTransactions_EmptyList() {
        when(trxJournalActiveRepository.countInFlightOnOrBefore(testDate)).thenReturn(0L);
        when(trxJournalActiveRepository.findEligibleForArchival(testDate)).thenReturn(List.of());

        ArchivalBatchResult result = dataArchivalService.archiveCompletedTransactions(testDate);

        assertThat(result.workingDate()).isEqualTo(testDate);
        assertThat(result.archivedTransactionsCount()).isEqualTo(0);
        assertThat(result.archivedEntriesCount()).isEqualTo(0);

        verify(trxJournalDoneRepository, never()).saveAll(any());
    }

    @Test
    @DisplayName("Archival: Successfully moves transactions and entries from Active to Done")
    void testArchiveCompletedTransactions_Success() {
        TrxJournalActive origTrx = TrxJournalActive.builder()
                .trxId(101L)
                .trxNumber("TRX-001")
                .channelId("MOBILE")
                .idempotencyKey("KEY-001")
                .trxCode("TRF_INTERNAL")
                .workingDate(testDate)
                .status("COMPLETED")
                .makerId("MAKER1")
                .isReversed("Y")
                .createdAt(OffsetDateTime.now())
                .build();

        TrxJournalActive revTrx = TrxJournalActive.builder()
                .trxId(102L)
                .trxNumber("TRX-002")
                .channelId("BRANCH")
                .idempotencyKey("KEY-002")
                .trxCode("TRF_INTERNAL")
                .workingDate(testDate)
                .status("COMPLETED")
                .makerId("TELLER1")
                .isReversed("N")
                .originalTrxId(101L)
                .reversalReason("Mistake")
                .createdAt(OffsetDateTime.now())
                .build();

        JournalEntryActive entry1 = JournalEntryActive.builder()
                .entryId(1001L)
                .trxId(101L)
                .entryGroup(1)
                .entryIndex(1)
                .accountId(1L)
                .glCode("4211")
                .drCr("D")
                .amount(new BigDecimal("1000.0000"))
                .currency("VND")
                .valueDate(testDate)
                .createdAt(OffsetDateTime.now())
                .build();

        JournalEntryActive entry2 = JournalEntryActive.builder()
                .entryId(1002L)
                .trxId(101L)
                .entryGroup(1)
                .entryIndex(2)
                .accountId(2L)
                .glCode("4211")
                .drCr("C")
                .amount(new BigDecimal("1000.0000"))
                .currency("VND")
                .valueDate(testDate)
                .createdAt(OffsetDateTime.now())
                .build();

        when(trxJournalActiveRepository.countInFlightOnOrBefore(testDate)).thenReturn(0L);
        when(trxJournalActiveRepository.findEligibleForArchival(testDate)).thenReturn(List.of(origTrx, revTrx));
        when(journalEntryActiveRepository.findByTrxIdIn(List.of(101L, 102L))).thenReturn(List.of(entry1, entry2));

        ArchivalBatchResult result = dataArchivalService.archiveCompletedTransactions(testDate);

        assertThat(result.workingDate()).isEqualTo(testDate);
        assertThat(result.archivedTransactionsCount()).isEqualTo(2);
        assertThat(result.archivedEntriesCount()).isEqualTo(2);

        // Verify copy to Done repositories
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<TrxJournalDone>> doneTrxCaptor = ArgumentCaptor.forClass(List.class);
        verify(trxJournalDoneRepository).saveAll(doneTrxCaptor.capture());
        List<TrxJournalDone> savedDoneTrxs = doneTrxCaptor.getValue();
        assertThat(savedDoneTrxs).hasSize(2);
        assertThat(savedDoneTrxs.get(1).getOriginalTrxId()).isEqualTo(101L); // preserved in cold storage

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<JournalEntryDone>> doneEntryCaptor = ArgumentCaptor.forClass(List.class);
        verify(journalEntryDoneRepository).saveAll(doneEntryCaptor.capture());
        assertThat(doneEntryCaptor.getValue()).hasSize(2);

        // Verify clean-up execution order:
        // 1. entries deleted
        verify(journalEntryActiveRepository).deleteByTrxIdIn(List.of(101L, 102L));
        // 2. self-referencing FKs disconnected
        verify(trxJournalActiveRepository).clearOriginalTrxIds(List.of(101L, 102L));
        // 3. active headers deleted
        verify(trxJournalActiveRepository).deleteAllById(List.of(101L, 102L));
    }
}

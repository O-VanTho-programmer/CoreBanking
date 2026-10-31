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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 3d: Hot-to-Cold Data Archival Service (MoveDataHis).
 * Archives completed/failed transactions and journal entries from hot active tables
 * (TRX_JOURNAL_ACTIVE, JOURNAL_ENTRY_ACTIVE) to cold historical tables
 * (TRX_JOURNAL_DONE, JOURNAL_ENTRY_DONE).
 *
 * Enforces:
 * 1. Anomaly Protection: IN_FLIGHT transactions block archival.
 * 2. Self-Referential FK Lineage Safety: Disconnects original_trx_id on active headers
 *    before deletion to prevent Oracle ORA-02292 errors, while preserving full lineage in DONE.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class DataArchivalService {

    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final TrxJournalDoneRepository trxJournalDoneRepository;
    private final JournalEntryDoneRepository journalEntryDoneRepository;
    private final WorkingDateService workingDateService;

    /**
     * Executes data archival for the specified working date.
     * If workingDate is null, the current system operational working date is used.
     *
     * @param workingDate the working date to archive
     * @return summary report of the archival batch run
     * @throws InFlightTransactionFoundException if any transaction is still IN_FLIGHT
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public ArchivalBatchResult archiveCompletedTransactions(LocalDate workingDate) {
        LocalDate effectiveWorkingDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting EOD Data Archival (MoveDataHis) for working date: {}", effectiveWorkingDate);

        // 1. Guard check: Ensure no dangling IN_FLIGHT transactions exist
        long inFlightCount = trxJournalActiveRepository.countInFlightOnOrBefore(effectiveWorkingDate);
        if (inFlightCount > 0) {
            log.error("Archival aborted: {} transactions are still IN_FLIGHT on or before {}",
                    inFlightCount, effectiveWorkingDate);
            throw new InFlightTransactionFoundException(inFlightCount, effectiveWorkingDate);
        }

        // 2. Fetch eligible completed or failed transactions
        List<TrxJournalActive> activeTrxs = trxJournalActiveRepository.findEligibleForArchival(effectiveWorkingDate);
        if (activeTrxs.isEmpty()) {
            log.info("No eligible transactions found for archival on or before {}", effectiveWorkingDate);
            return ArchivalBatchResult.builder()
                    .workingDate(effectiveWorkingDate)
                    .archivedTransactionsCount(0)
                    .archivedEntriesCount(0)
                    .completedAt(OffsetDateTime.now())
                    .build();
        }

        List<Long> trxIds = activeTrxs.stream().map(TrxJournalActive::getTrxId).toList();
        List<JournalEntryActive> activeEntries = journalEntryActiveRepository.findByTrxIdIn(trxIds);

        log.info("Archiving {} transactions and {} journal entries from Hot to Cold storage",
                activeTrxs.size(), activeEntries.size());

        // 3. Map and save to cold tables (TRX_JOURNAL_DONE, JOURNAL_ENTRY_DONE)
        List<TrxJournalDone> doneTrxs = activeTrxs.stream()
                .map(a -> TrxJournalDone.builder()
                        .trxId(a.getTrxId())
                        .trxNumber(a.getTrxNumber())
                        .channelId(a.getChannelId())
                        .idempotencyKey(a.getIdempotencyKey())
                        .trxCode(a.getTrxCode())
                        .workingDate(a.getWorkingDate())
                        .status(a.getStatus())
                        .makerId(a.getMakerId())
                        .checkerId(a.getCheckerId())
                        .isReversed(a.getIsReversed())
                        .originalTrxId(a.getOriginalTrxId())
                        .reversalReason(a.getReversalReason())
                        .payloadData(a.getPayloadData())
                        .createdAt(a.getCreatedAt())
                        .archivedAt(OffsetDateTime.now())
                        .build())
                .toList();

        trxJournalDoneRepository.saveAll(doneTrxs);

        List<JournalEntryDone> doneEntries = activeEntries.stream()
                .map(e -> JournalEntryDone.builder()
                        .entryId(e.getEntryId())
                        .trxId(e.getTrxId())
                        .entryGroup(e.getEntryGroup())
                        .entryIndex(e.getEntryIndex())
                        .accountId(e.getAccountId())
                        .glCode(e.getGlCode())
                        .drCr(e.getDrCr())
                        .amount(e.getAmount())
                        .currency(e.getCurrency())
                        .valueDate(e.getValueDate())
                        .createdAt(e.getCreatedAt())
                        .archivedAt(OffsetDateTime.now())
                        .build())
                .toList();

        journalEntryDoneRepository.saveAll(doneEntries);

        // 4. Clean up Hot tables:
        // 4a. Delete child journal entries first
        journalEntryActiveRepository.deleteByTrxIdIn(trxIds);

        // 4b. Disconnect self-referencing reversal FKs (original_trx_id) to avoid ORA-02292
        trxJournalActiveRepository.clearOriginalTrxIds(trxIds);

        // 4c. Delete active transaction headers
        trxJournalActiveRepository.deleteAllById(trxIds);

        log.info("Successfully archived {} transactions and {} journal entries for working date {}",
                doneTrxs.size(), doneEntries.size(), effectiveWorkingDate);

        return ArchivalBatchResult.builder()
                .workingDate(effectiveWorkingDate)
                .archivedTransactionsCount(doneTrxs.size())
                .archivedEntriesCount(doneEntries.size())
                .completedAt(OffsetDateTime.now())
                .build();
    }
}

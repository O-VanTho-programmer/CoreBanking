package com.cbs.eod.dto;

import lombok.Builder;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Summary result of an EOD Data Archival (MoveDataHis) run.
 */
@Builder
public record ArchivalBatchResult(
        LocalDate workingDate,
        int archivedTransactionsCount,
        int archivedEntriesCount,
        OffsetDateTime completedAt
) {
}

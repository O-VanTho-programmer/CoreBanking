package com.cbs.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table TRX_JOURNAL_ACTIVE.
 * Hot day-transaction journal header with composite idempotency key (channel_id, idempotency_key).
 */
@Entity
@Table(name = "TRX_JOURNAL_ACTIVE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class TrxJournalActive {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "trx_id", nullable = false)
    private Long trxId;

    @Column(name = "trx_number", length = 36, nullable = false, unique = true)
    private String trxNumber;

    @Column(name = "channel_id", length = 30, nullable = false)
    private String channelId;

    @Column(name = "idempotency_key", length = 64, nullable = false)
    private String idempotencyKey;

    @Column(name = "trx_code", length = 30, nullable = false)
    private String trxCode;

    @Column(name = "working_date", nullable = false)
    private LocalDate workingDate;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "IN_FLIGHT";

    @Column(name = "maker_id", length = 30, nullable = false)
    private String makerId;

    @Column(name = "checker_id", length = 30)
    private String checkerId;

    @Column(name = "is_reversed", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isReversed = "N";

    @Column(name = "original_trx_id")
    private Long originalTrxId;

    @Column(name = "reversal_reason", length = 255)
    private String reversalReason;

    @Lob
    @Column(name = "payload_data")
    private String payloadData;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

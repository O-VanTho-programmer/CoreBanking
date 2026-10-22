package com.cbs.transaction.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table JOURNAL_ENTRY_ACTIVE.
 * Immutable accounting ledger lines for active day transactions.
 */
@Entity
@Table(name = "JOURNAL_ENTRY_ACTIVE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JournalEntryActive {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "entry_id", nullable = false)
    private Long entryId;

    @Column(name = "trx_id", nullable = false)
    private Long trxId;

    @Column(name = "entry_group", nullable = false)
    @Builder.Default
    private Integer entryGroup = 1;

    @Column(name = "entry_index", nullable = false)
    private Integer entryIndex;

    @Column(name = "account_id")
    private Long accountId;

    @Column(name = "gl_code", length = 20, nullable = false)
    private String glCode;

    @Column(name = "dr_cr", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String drCr;

    @Column(name = "amount", nullable = false)
    private BigDecimal amount;

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "value_date", nullable = false)
    private LocalDate valueDate;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}

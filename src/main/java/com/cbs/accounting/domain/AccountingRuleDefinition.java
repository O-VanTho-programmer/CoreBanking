package com.cbs.depositing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDate;

/**
 * Entity mapping table ACCOUNTING_RULE_DEFINITION.
 * Stores the Governed Posting Rule Matrix for all financial transactions.
 */
@Entity
@Table(name = "ACCOUNTING_RULE_DEFINITION")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AccountingRuleDefinition {

    @Id
    @Column(name = "rule_id", nullable = false)
    private Long ruleId;

    @Column(name = "trx_code", length = 30, nullable = false)
    private String trxCode;

    @Column(name = "entry_group", nullable = false)
    @Builder.Default
    private Integer entryGroup = 1;

    @Column(name = "entry_index", nullable = false)
    private Integer entryIndex;

    @Column(name = "account_role", length = 30, nullable = false)
    private String accountRole;

    @Column(name = "abstract_acct_name", length = 50)
    private String abstractAcctName;

    @Column(name = "debit_or_credit", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String debitOrCredit;

    @Column(name = "amount_source", length = 30, nullable = false)
    private String amountSource;

    @Column(name = "requires_fee", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String requiresFee = "N";

    @Column(name = "narration_template", length = 255, nullable = false)
    private String narrationTemplate;

    @Column(name = "effective_from", nullable = false)
    private LocalDate effectiveFrom;

    @Column(name = "effective_to", nullable = false)
    @Builder.Default
    private LocalDate effectiveTo = LocalDate.of(9999, 12, 31);

    @Column(name = "is_active", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isActive = "Y";
}

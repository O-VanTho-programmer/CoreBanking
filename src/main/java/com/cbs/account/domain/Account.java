package com.cbs.account.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
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
 * Entity mapping table ACCOUNT (Demand Deposit / CASA Accounts).
 * Enforces Invariant 2: Available = Ledger - Hold.
 */
@Entity
@Table(name = "ACCOUNT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "account_id", nullable = false)
    private Long accountId;

    @Column(name = "account_number", length = 34, nullable = false, unique = true)
    private String accountNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "product_code", length = 10, nullable = false)
    private String productCode;

    @Column(name = "product_type", length = 10, nullable = false)
    @Builder.Default
    private String productType = "CASA";

    @Column(name = "gl_code", length = 20, nullable = false)
    private String glCode;

    @Column(name = "branch_code", length = 10, nullable = false)
    private String branchCode;

    @Column(name = "currency", columnDefinition = "CHAR(3)", length = 3, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    private String currency;

    @Column(name = "ledger_balance", nullable = false)
    @Builder.Default
    private BigDecimal ledgerBalance = BigDecimal.ZERO.setScale(4);

    @Column(name = "hold_balance", nullable = false)
    @Builder.Default
    private BigDecimal holdBalance = BigDecimal.ZERO.setScale(4);

    @Column(name = "accrued_interest", nullable = false)
    @Builder.Default
    private BigDecimal accruedInterest = BigDecimal.ZERO.setScale(4);

    @Column(name = "base_interest_rate", nullable = false)
    @Builder.Default
    private BigDecimal baseInterestRate = new BigDecimal("0.0050");

    @Column(name = "interest_margin", nullable = false)
    @Builder.Default
    private BigDecimal interestMargin = BigDecimal.ZERO;

    @Column(name = "effective_rate", nullable = false)
    @Builder.Default
    private BigDecimal effectiveRate = new BigDecimal("0.0050");

    @Column(name = "term_months")
    private Integer termMonths;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Column(name = "rollover_option", length = 30)
    private String rolloverOption;

    @Column(name = "margin_reason", length = 50)
    private String marginReason;

    @Column(name = "linked_casa_account_id")
    private Long linkedCasaAccountId;

    @Column(name = "status", length = 30, nullable = false)
    @Builder.Default
    private String status = "ACTIVE";

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "created_working_date", nullable = false)
    private LocalDate createdWorkingDate;

    /**
     * Invariant 2: Calculates current available balance.
     */
    public BigDecimal getAvailableBalance() {
        return this.ledgerBalance.subtract(this.holdBalance);
    }
}

package com.cbs.lending.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table LOAN_ACCOUNT.
 * Master loan agreement and outstanding balance record.
 */
@Entity
@Table(name = "LOAN_ACCOUNT")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "loan_account_number", length = 34, nullable = false, unique = true)
    private String loanAccountNumber;

    @Column(name = "customer_id", nullable = false)
    private Long customerId;

    @Column(name = "linked_casa_account_id", nullable = false)
    private Long linkedCasaAccountId;

    @Column(name = "product_code", length = 20, nullable = false)
    private String productCode;

    @Column(name = "principal_amount", nullable = false)
    private BigDecimal principalAmount;

    @Column(name = "remaining_principal", nullable = false)
    private BigDecimal remainingPrincipal;

    @Column(name = "annual_interest_rate", nullable = false)
    private BigDecimal annualInterestRate;

    @Column(name = "overdue_interest_rate", nullable = false)
    private BigDecimal overdueInterestRate;

    @Column(name = "term_months", nullable = false)
    private Integer termMonths;

    @Column(name = "repayment_type", length = 20, nullable = false)
    private String repaymentType;

    @Column(name = "day_count_basis", length = 10, nullable = false)
    @Builder.Default
    private String dayCountBasis = "ACT_365";

    @Column(name = "loan_status", length = 20, nullable = false)
    @Builder.Default
    private String loanStatus = "APPROVED";

    @Column(name = "disbursed_at")
    private OffsetDateTime disbursedAt;

    @Column(name = "maturity_date")
    private LocalDate maturityDate;

    @Version
    @Column(name = "version", nullable = false)
    @Builder.Default
    private Long version = 0L;

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

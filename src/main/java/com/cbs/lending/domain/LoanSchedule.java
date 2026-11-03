package com.cbs.lending.domain;

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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Entity mapping table LOAN_SCHEDULE.
 * Repayment schedule installment row per billing cycle.
 */
@Entity
@Table(name = "LOAN_SCHEDULE")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LoanSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "cbs_global_seq_gen")
    @SequenceGenerator(name = "cbs_global_seq_gen", sequenceName = "CBS_GLOBAL_SEQ", allocationSize = 1)
    @Column(name = "schedule_id", nullable = false)
    private Long scheduleId;

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "period_number", nullable = false)
    private Integer periodNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(name = "principal_due", nullable = false)
    private BigDecimal principalDue;

    @Column(name = "interest_due", nullable = false)
    private BigDecimal interestDue;

    @Column(name = "total_installment", nullable = false)
    private BigDecimal totalInstallment;

    @Column(name = "principal_paid", nullable = false)
    @Builder.Default
    private BigDecimal principalPaid = BigDecimal.ZERO;

    @Column(name = "interest_paid", nullable = false)
    @Builder.Default
    private BigDecimal interestPaid = BigDecimal.ZERO;

    @Column(name = "penalty_due", nullable = false)
    @Builder.Default
    private BigDecimal penaltyDue = BigDecimal.ZERO;

    @Column(name = "penalty_paid", nullable = false)
    @Builder.Default
    private BigDecimal penaltyPaid = BigDecimal.ZERO;

    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private String status = "PENDING";

    @Column(name = "created_at", nullable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();

    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;
}

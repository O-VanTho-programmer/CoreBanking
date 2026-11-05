package com.cbs.eod.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.io.Serializable;
import java.time.LocalDate;

/**
 * Composite Primary Key for LOAN_BALANCE_BUCKET (loan_id, working_date).
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class LoanBalanceBucketId implements Serializable {

    @Column(name = "loan_id", nullable = false)
    private Long loanId;

    @Column(name = "working_date", nullable = false)
    private LocalDate workingDate;
}

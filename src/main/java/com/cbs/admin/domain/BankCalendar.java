package com.cbs.admin.domain;

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
import java.time.OffsetDateTime;

/**
 * Entity mapping BANK_CALENDAR table.
 * Governs business working days, weekends, holidays, and fiscal periods (Invariant 4).
 */
@Entity
@Table(name = "BANK_CALENDAR")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BankCalendar {

    @Id
    @Column(name = "calendar_date", nullable = false)
    private LocalDate calendarDate;

    @Column(name = "working_date", nullable = false)
    private LocalDate workingDate;

    @Column(name = "is_working_day", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isWorkingDay = "Y";

    @Column(name = "fiscal_year", nullable = false)
    private Integer fiscalYear;

    @Column(name = "fiscal_period", nullable = false)
    private Integer fiscalPeriod;

    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private OffsetDateTime updatedAt = OffsetDateTime.now();
}

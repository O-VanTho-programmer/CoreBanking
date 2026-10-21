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
 * Singleton entity mapping SYSTEM_OPERATIONAL_STATUS.
 * Governs current operational working date and system cutoff state (Invariant 4).
 */
@Entity
@Table(name = "SYSTEM_OPERATIONAL_STATUS")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemOperationalStatus {

    public static final Long SINGLETON_ID = 1L;

    @Id
    @Column(name = "status_id", nullable = false)
    @Builder.Default
    private Long statusId = SINGLETON_ID;

    @Column(name = "current_working_date", nullable = false)
    private LocalDate currentWorkingDate;

    @Column(name = "is_system_cutoff", columnDefinition = "CHAR(1)", length = 1, nullable = false)
    @JdbcTypeCode(SqlTypes.CHAR)
    @Builder.Default
    private String isSystemCutoff = "N";

    @Column(name = "last_eod_completed_at")
    private OffsetDateTime lastEodCompletedAt;
}

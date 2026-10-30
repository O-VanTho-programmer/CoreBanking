package com.cbs.admin.service;

import com.cbs.admin.domain.SystemOperationalStatus;
import com.cbs.admin.repository.BankCalendarRepository;
import com.cbs.admin.repository.SystemOperationalStatusRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class WorkingDateServiceTest {

    @Mock
    private SystemOperationalStatusRepository statusRepository;

    @Mock
    private BankCalendarRepository calendarRepository;

    @InjectMocks
    private WorkingDateService workingDateService;

    private final LocalDate currentWorkingDate = LocalDate.of(2026, 10, 13);

    @Test
    @DisplayName("Working Date: Retrieves current working date from database singleton")
    void shouldReturnCurrentWorkingDate() {
        SystemOperationalStatus status = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("N")
                .build();

        when(statusRepository.findById(1L)).thenReturn(Optional.of(status));

        LocalDate result = workingDateService.getCurrentWorkingDate();
        assertThat(result).isEqualTo(currentWorkingDate);
    }

    @Test
    @DisplayName("Working Date: Throws IllegalStateException when singleton status row is missing")
    void shouldThrowExceptionWhenStatusRecordMissing() {
        when(statusRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workingDateService.getCurrentWorkingDate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SYSTEM_OPERATIONAL_STATUS record (id=1) is missing");
    }

    @Test
    @DisplayName("Cutoff: isSystemCutoff returns true when flag is 'Y' and false when 'N'")
    void shouldReportCutoffStatusAccurately() {
        SystemOperationalStatus activeStatus = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("N")
                .build();
        when(statusRepository.findById(1L)).thenReturn(Optional.of(activeStatus));
        assertThat(workingDateService.isSystemCutoff()).isFalse();

        SystemOperationalStatus cutoffStatus = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("Y")
                .build();
        when(statusRepository.findById(1L)).thenReturn(Optional.of(cutoffStatus));
        assertThat(workingDateService.isSystemCutoff()).isTrue();
    }

    @Test
    @DisplayName("Cutoff: setCutoff, enableCutoff, and disableCutoff update singleton entity")
    void shouldToggleCutoffStatus() {
        SystemOperationalStatus status = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("N")
                .build();
        when(statusRepository.findById(1L)).thenReturn(Optional.of(status));

        workingDateService.enableCutoff();
        assertThat(status.getIsSystemCutoff()).isEqualTo("Y");
        verify(statusRepository).save(status);

        workingDateService.disableCutoff();
        assertThat(status.getIsSystemCutoff()).isEqualTo("N");
        verify(statusRepository, times(2)).save(status);
    }

    @Test
    @DisplayName("Rollover: Rollover advances date to next working day from calendar and opens cutoff")
    void shouldRolloverWorkingDateSuccessfully() {
        SystemOperationalStatus status = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("Y")
                .build();
        when(statusRepository.findById(1L)).thenReturn(Optional.of(status));

        LocalDate nextWorkingDate = LocalDate.of(2026, 10, 14);
        when(calendarRepository.findNextWorkingDay(currentWorkingDate)).thenReturn(Optional.of(nextWorkingDate));

        LocalDate rolledDate = workingDateService.rolloverWorkingDate();

        assertThat(rolledDate).isEqualTo(nextWorkingDate);
        assertThat(status.getCurrentWorkingDate()).isEqualTo(nextWorkingDate);
        assertThat(status.getIsSystemCutoff()).isEqualTo("N");
        assertThat(status.getLastEodCompletedAt()).isNotNull();
        verify(statusRepository).save(status);
    }

    @Test
    @DisplayName("Rollover: Throws IllegalStateException when no next working day is configured in calendar")
    void shouldThrowExceptionWhenNextWorkingDayMissing() {
        SystemOperationalStatus status = SystemOperationalStatus.builder()
                .statusId(1L)
                .currentWorkingDate(currentWorkingDate)
                .isSystemCutoff("Y")
                .build();
        when(statusRepository.findById(1L)).thenReturn(Optional.of(status));
        when(calendarRepository.findNextWorkingDay(currentWorkingDate)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> workingDateService.rolloverWorkingDate())
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("no upcoming working day configured in BANK_CALENDAR");

        verify(statusRepository, never()).save(any());
    }
}

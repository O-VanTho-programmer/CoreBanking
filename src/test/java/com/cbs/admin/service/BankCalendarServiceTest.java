package com.cbs.admin.service;

import com.cbs.admin.domain.BankCalendar;
import com.cbs.admin.repository.BankCalendarRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BankCalendarServiceTest {

    @Mock
    private BankCalendarRepository calendarRepository;

    @InjectMocks
    private BankCalendarService calendarService;

    private final LocalDate testDate = LocalDate.of(2026, 10, 14);

    @Test
    @DisplayName("isWorkingDay: Returns true for 'Y', false for 'N', and false for unconfigured date")
    void shouldDetermineWorkingDayAccurately() {
        BankCalendar workingDay = BankCalendar.builder()
                .calendarDate(testDate)
                .isWorkingDay("Y")
                .build();
        when(calendarRepository.findByCalendarDate(testDate)).thenReturn(Optional.of(workingDay));
        assertThat(calendarService.isWorkingDay(testDate)).isTrue();

        LocalDate holiday = LocalDate.of(2026, 10, 17);
        BankCalendar nonWorkingDay = BankCalendar.builder()
                .calendarDate(holiday)
                .isWorkingDay("N")
                .build();
        when(calendarRepository.findByCalendarDate(holiday)).thenReturn(Optional.of(nonWorkingDay));
        assertThat(calendarService.isWorkingDay(holiday)).isFalse();

        LocalDate missingDate = LocalDate.of(2026, 12, 25);
        when(calendarRepository.findByCalendarDate(missingDate)).thenReturn(Optional.empty());
        assertThat(calendarService.isWorkingDay(missingDate)).isFalse();
    }

    @Test
    @DisplayName("getNextWorkingDay: Returns next working day from repository or throws exception")
    void shouldGetNextWorkingDayOrThrow() {
        LocalDate nextDay = LocalDate.of(2026, 10, 15);
        when(calendarRepository.findNextWorkingDay(testDate)).thenReturn(Optional.of(nextDay));

        assertThat(calendarService.getNextWorkingDay(testDate)).isEqualTo(nextDay);

        when(calendarRepository.findNextWorkingDay(testDate)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> calendarService.getNextWorkingDay(testDate))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No working day found in BANK_CALENDAR");
    }

    @Test
    @DisplayName("configureDay: Configures existing day as holiday or working day")
    void shouldConfigureCalendarDay() {
        BankCalendar existing = BankCalendar.builder()
                .calendarDate(testDate)
                .isWorkingDay("Y")
                .build();
        when(calendarRepository.findByCalendarDate(testDate)).thenReturn(Optional.of(existing));
        when(calendarRepository.save(any(BankCalendar.class))).thenAnswer(inv -> inv.getArgument(0));

        BankCalendar updated = calendarService.configureDay(testDate, false);

        assertThat(updated.getIsWorkingDay()).isEqualTo("N");
        verify(calendarRepository).save(existing);
    }

    @Test
    @DisplayName("getCalendarRange: Queries range ordered chronologically")
    void shouldRetrieveCalendarRange() {
        LocalDate start = LocalDate.of(2026, 10, 13);
        LocalDate end = LocalDate.of(2026, 10, 15);

        List<BankCalendar> mockList = List.of(
                BankCalendar.builder().calendarDate(start).build(),
                BankCalendar.builder().calendarDate(end).build()
        );
        when(calendarRepository.findByDateRange(start, end)).thenReturn(mockList);

        List<BankCalendar> result = calendarService.getCalendarRange(start, end);
        assertThat(result).hasSize(2);
    }
}

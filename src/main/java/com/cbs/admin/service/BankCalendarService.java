package com.cbs.admin.service;

import com.cbs.admin.domain.BankCalendar;
import com.cbs.admin.repository.BankCalendarRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Bank Calendar Service.
 * Manages bank calendar days, working day determinations, and holiday configurations.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BankCalendarService {

    private final BankCalendarRepository calendarRepository;

    @Transactional(readOnly = true)
    public boolean isWorkingDay(LocalDate date) {
        return calendarRepository.findByCalendarDate(date)
                .map(cal -> "Y".equals(cal.getIsWorkingDay()))
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public LocalDate getNextWorkingDay(LocalDate afterDate) {
        return calendarRepository.findNextWorkingDay(afterDate)
                .orElseThrow(() -> new IllegalStateException(
                        "No working day found in BANK_CALENDAR after date: " + afterDate));
    }

    @Transactional(readOnly = true)
    public List<BankCalendar> getCalendarRange(LocalDate startDate, LocalDate endDate) {
        return calendarRepository.findByDateRange(startDate, endDate);
    }

    /**
     * Configures a calendar date as working day or holiday/non-working day.
     */
    @Transactional
    public BankCalendar configureDay(LocalDate date, boolean isWorkingDay) {
        BankCalendar entry = calendarRepository.findByCalendarDate(date)
                .orElseGet(() -> BankCalendar.builder()
                        .calendarDate(date)
                        .workingDate(date)
                        .fiscalYear(date.getYear())
                        .fiscalPeriod(date.getMonthValue())
                        .build());

        entry.setIsWorkingDay(isWorkingDay ? "Y" : "N");
        entry.setUpdatedAt(OffsetDateTime.now());
        log.info("Configured calendar date {} as isWorkingDay={}", date, isWorkingDay);
        return calendarRepository.save(entry);
    }
}

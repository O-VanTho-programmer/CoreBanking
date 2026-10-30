package com.cbs.admin.repository;

import com.cbs.admin.domain.BankCalendar;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface BankCalendarRepository extends JpaRepository<BankCalendar, LocalDate> {

    Optional<BankCalendar> findByCalendarDate(LocalDate calendarDate);

    /**
     * Determines the earliest upcoming working day strictly after the given date.
     * Skips non-working days (weekends, holidays).
     */
    @Query("SELECT MIN(b.calendarDate) FROM BankCalendar b WHERE b.calendarDate > :date AND b.isWorkingDay = 'Y'")
    Optional<LocalDate> findNextWorkingDay(@Param("date") LocalDate date);

    /**
     * Retrieves calendar entries within a date range ordered chronologically.
     */
    @Query("SELECT b FROM BankCalendar b WHERE b.calendarDate BETWEEN :startDate AND :endDate ORDER BY b.calendarDate ASC")
    List<BankCalendar> findByDateRange(@Param("startDate") LocalDate startDate, @Param("endDate") LocalDate endDate);

    boolean existsByCalendarDateAndIsWorkingDay(LocalDate calendarDate, String isWorkingDay);
}

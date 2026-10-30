package com.cbs.admin.service;

import com.cbs.admin.domain.SystemOperationalStatus;
import com.cbs.admin.repository.BankCalendarRepository;
import com.cbs.admin.repository.SystemOperationalStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * Operational Working Date Service.
 * Implements Invariant 4: Decouples business logic from physical clock (LocalDate.now()).
 * Manages system cut-off mode and operational date rollover across bank business days.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkingDateService {

    private final SystemOperationalStatusRepository statusRepository;
    private final BankCalendarRepository calendarRepository;

    @Transactional(readOnly = true)
    public LocalDate getCurrentWorkingDate() {
        return getStatus().getCurrentWorkingDate();
    }

    @Transactional(readOnly = true)
    public boolean isSystemCutoff() {
        return "Y".equals(getStatus().getIsSystemCutoff());
    }

    /**
     * Enables or disables system cut-off mode.
     * When cut-off is enabled, online daytime transaction mutations are rejected.
     */
    @Transactional
    public void setCutoff(boolean cutoff) {
        SystemOperationalStatus status = getStatus();
        status.setIsSystemCutoff(cutoff ? "Y" : "N");
        statusRepository.save(status);
        log.info("System operational cutoff set to: {}", cutoff);
    }

    @Transactional
    public void enableCutoff() {
        setCutoff(true);
    }

    @Transactional
    public void disableCutoff() {
        setCutoff(false);
    }

    /**
     * Rolls over the system operational working date to the next valid working day in BANK_CALENDAR.
     * Automatically resets cut-off to 'N' and timestamps last_eod_completed_at.
     *
     * @return the new operational working date
     */
    @Transactional
    public LocalDate rolloverWorkingDate() {
        SystemOperationalStatus status = getStatus();
        LocalDate currentDate = status.getCurrentWorkingDate();

        LocalDate nextWorkingDate = calendarRepository.findNextWorkingDay(currentDate)
                .orElseThrow(() -> new IllegalStateException(
                        "Cannot rollover: no upcoming working day configured in BANK_CALENDAR after " + currentDate));

        status.setCurrentWorkingDate(nextWorkingDate);
        status.setIsSystemCutoff("N");
        status.setLastEodCompletedAt(OffsetDateTime.now());
        statusRepository.save(status);

        log.info("System operational working date successfully rolled over from {} to {}", currentDate, nextWorkingDate);
        return nextWorkingDate;
    }

    private SystemOperationalStatus getStatus() {
        return statusRepository.findById(SystemOperationalStatus.SINGLETON_ID)
                .orElseThrow(() -> new IllegalStateException(
                        "SYSTEM_OPERATIONAL_STATUS record (id=1) is missing from database"));
    }
}

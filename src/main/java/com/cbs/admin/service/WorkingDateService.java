package com.cbs.admin.service;

import com.cbs.admin.domain.SystemOperationalStatus;
import com.cbs.admin.repository.SystemOperationalStatusRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;

/**
 * Operational Working Date Service.
 * Implements Invariant 4: Decouples business logic from physical clock (LocalDate.now()).
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class WorkingDateService {

    private final SystemOperationalStatusRepository statusRepository;

    @Transactional(readOnly = true)
    public LocalDate getCurrentWorkingDate() {
        return statusRepository.findById(SystemOperationalStatus.SINGLETON_ID)
                .map(SystemOperationalStatus::getCurrentWorkingDate)
                .orElseThrow(() -> new IllegalStateException(
                        "SYSTEM_OPERATIONAL_STATUS record (id=1) is missing from database"));
    }

    @Transactional(readOnly = true)
    public boolean isSystemCutoff() {
        return statusRepository.findById(SystemOperationalStatus.SINGLETON_ID)
                .map(s -> "Y".equals(s.getIsSystemCutoff()))
                .orElse(false);
    }
}

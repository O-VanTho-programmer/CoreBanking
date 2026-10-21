package com.cbs.admin.repository;

import com.cbs.admin.domain.SystemOperationalStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemOperationalStatusRepository extends JpaRepository<SystemOperationalStatus, Long> {
}

package com.cbs.accounting.repository;

import com.cbs.accounting.domain.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, String> {
}

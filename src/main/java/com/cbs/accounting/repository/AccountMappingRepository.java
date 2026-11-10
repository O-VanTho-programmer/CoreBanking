package com.cbs.depositing.repository;

import com.cbs.depositing.domain.AccountMapping;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface AccountMappingRepository extends JpaRepository<AccountMapping, String> {
}

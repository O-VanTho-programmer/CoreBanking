package com.cbs.account.repository;

import com.cbs.account.domain.AccountIfc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AccountIfcRepository extends JpaRepository<AccountIfc, Long> {
    List<AccountIfc> findByAccountId(Long accountId);
    Optional<AccountIfc> findByAccountIdAndIfcCode(Long accountId, String ifcCode);
}

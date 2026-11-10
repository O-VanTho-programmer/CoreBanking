package com.cbs.deposit.repository;

import com.cbs.deposit.domain.AccountHold;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AccountHoldRepository extends JpaRepository<AccountHold, Long> {

    List<AccountHold> findByAccountIdAndStatus(Long accountId, String status);

    List<AccountHold> findByAccountId(Long accountId);
}

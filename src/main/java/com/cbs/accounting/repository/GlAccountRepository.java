package com.cbs.depositing.repository;

import com.cbs.depositing.domain.GlAccount;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface GlAccountRepository extends JpaRepository<GlAccount, String> {

    Optional<GlAccount> findByGlCodeAndIsPostable(String glCode, String isPostable);
}

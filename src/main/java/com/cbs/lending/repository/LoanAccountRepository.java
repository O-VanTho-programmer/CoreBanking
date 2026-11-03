package com.cbs.lending.repository;

import com.cbs.lending.domain.LoanAccount;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountRepository extends JpaRepository<LoanAccount, Long> {

    Optional<LoanAccount> findByLoanAccountNumber(String loanAccountNumber);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT l FROM LoanAccount l WHERE l.loanId = :loanId")
    Optional<LoanAccount> findByIdForUpdate(@Param("loanId") Long loanId);

    List<LoanAccount> findByCustomerId(Long customerId);

    List<LoanAccount> findByLoanStatus(String loanStatus);
}

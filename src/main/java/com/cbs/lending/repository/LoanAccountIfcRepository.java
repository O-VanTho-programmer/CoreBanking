package com.cbs.lending.repository;

import com.cbs.lending.domain.LoanAccountIfc;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface LoanAccountIfcRepository extends JpaRepository<LoanAccountIfc, Long> {
    List<LoanAccountIfc> findByLoanId(Long loanId);
    Optional<LoanAccountIfc> findByLoanIdAndIfcCode(Long loanId, String ifcCode);
}

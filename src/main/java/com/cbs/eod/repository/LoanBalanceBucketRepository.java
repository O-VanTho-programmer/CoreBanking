package com.cbs.eod.repository;

import com.cbs.eod.domain.LoanBalanceBucket;
import com.cbs.eod.domain.LoanBalanceBucketId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

@Repository
public interface LoanBalanceBucketRepository extends JpaRepository<LoanBalanceBucket, LoanBalanceBucketId> {

    @Query("SELECT b FROM LoanBalanceBucket b WHERE b.id.workingDate = :workingDate")
    List<LoanBalanceBucket> findByWorkingDate(@Param("workingDate") LocalDate workingDate);

    @Query("SELECT b FROM LoanBalanceBucket b WHERE b.id.loanId = :loanId ORDER BY b.id.workingDate DESC")
    List<LoanBalanceBucket> findByLoanIdOrderByWorkingDateDesc(@Param("loanId") Long loanId);
}

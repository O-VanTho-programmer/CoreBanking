package com.cbs.lending.repository;

import com.cbs.lending.domain.LoanSchedule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Repository
public interface LoanScheduleRepository extends JpaRepository<LoanSchedule, Long> {

    List<LoanSchedule> findByLoanIdOrderByPeriodNumberAsc(Long loanId);

    Optional<LoanSchedule> findByLoanIdAndPeriodNumber(Long loanId, Integer periodNumber);

    @Query("SELECT s FROM LoanSchedule s WHERE s.dueDate <= :date AND s.status IN ('PENDING', 'PARTIALLY_PAID', 'OVERDUE') ORDER BY s.dueDate ASC, s.periodNumber ASC")
    List<LoanSchedule> findDueSchedulesOnOrBefore(@Param("date") LocalDate date);

    @Query("SELECT s FROM LoanSchedule s WHERE s.loanId = :loanId AND s.dueDate <= :date AND s.status IN ('PENDING', 'PARTIALLY_PAID', 'OVERDUE') ORDER BY s.periodNumber ASC")
    List<LoanSchedule> findDueSchedulesByLoanIdOnOrBefore(@Param("loanId") Long loanId, @Param("date") LocalDate date);
}

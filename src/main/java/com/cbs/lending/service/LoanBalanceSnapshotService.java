package com.cbs.lending.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.eod.domain.LoanBalanceBucket;
import com.cbs.eod.domain.LoanBalanceBucketId;
import com.cbs.eod.repository.LoanBalanceBucketRepository;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Phase 4e: Loan Balance Snapshot Service.
 * Freezes end-of-day loan balance, DPD, group classification, and loss provisions into LOAN_BALANCE_BUCKET.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanBalanceSnapshotService {

    private final LoanAccountRepository loanAccountRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final LoanBalanceBucketRepository loanBalanceBucketRepository;
    private final WorkingDateService workingDateService;

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public int takeDailyLoanSnapshot(LocalDate workingDate) {
        LocalDate effectiveDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting EOD Loan Balance Snapshot for working date: {}", effectiveDate);

        List<LoanAccount> loans = loanAccountRepository.findAll();
        int snapshotCount = 0;

        for (LoanAccount loan : loans) {
            // Snapshot active, overdue, and any accounts with principal or provisions
            if ("APPROVED".equals(loan.getLoanStatus()) && loan.getDisbursedAt() == null) {
                continue; // Skip approved but undisbursed loans
            }

            BigDecimal remaining = loan.getRemainingPrincipal() != null ? loan.getRemainingPrincipal() : BigDecimal.ZERO;
            List<LoanSchedule> dueSchedules = loanScheduleRepository.findDueSchedulesByLoanIdOnOrBefore(loan.getLoanId(), effectiveDate);

            BigDecimal pastDuePrincipal = BigDecimal.ZERO;
            BigDecimal overdueInterest = BigDecimal.ZERO;
            BigDecimal inTermInterest = BigDecimal.ZERO;
            BigDecimal penaltyDue = BigDecimal.ZERO;

            for (LoanSchedule s : dueSchedules) {
                BigDecimal prUnpaid = s.getPrincipalDue().subtract(s.getPrincipalPaid()).max(BigDecimal.ZERO);
                BigDecimal inUnpaid = s.getInterestDue().subtract(s.getInterestPaid()).max(BigDecimal.ZERO);
                BigDecimal penUnpaid = s.getPenaltyDue().subtract(s.getPenaltyPaid()).max(BigDecimal.ZERO);

                if (s.getDueDate().isBefore(effectiveDate)) {
                    pastDuePrincipal = pastDuePrincipal.add(prUnpaid);
                    overdueInterest = overdueInterest.add(inUnpaid);
                } else {
                    inTermInterest = inTermInterest.add(inUnpaid);
                }
                penaltyDue = penaltyDue.add(penUnpaid);
            }

            BigDecimal inTermPrincipal = remaining.subtract(pastDuePrincipal).max(BigDecimal.ZERO);

            LoanBalanceBucket bucket = LoanBalanceBucket.builder()
                    .id(new LoanBalanceBucketId(loan.getLoanId(), effectiveDate))
                    .outstandingPrincipal(remaining)
                    .pastDuePrincipal(pastDuePrincipal)
                    .inTermPrincipal(inTermPrincipal)
                    .accruedInterest(inTermInterest)
                    .overdueInterest(overdueInterest)
                    .penaltyDue(penaltyDue)
                    .loanGroup(loan.getLoanGroup() != null ? loan.getLoanGroup() : 1)
                    .daysPastDue(loan.getDaysPastDue() != null ? loan.getDaysPastDue() : 0)
                    .generalProvision(loan.getGeneralProvisionAmount() != null ? loan.getGeneralProvisionAmount() : BigDecimal.ZERO)
                    .specificProvision(loan.getSpecificProvisionAmount() != null ? loan.getSpecificProvisionAmount() : BigDecimal.ZERO)
                    .createdAt(OffsetDateTime.now())
                    .build();

            loanBalanceBucketRepository.save(bucket);
            snapshotCount++;
        }

        log.info("Completed EOD Loan Balance Snapshot for working date {}: captured {} loan records",
                effectiveDate, snapshotCount);
        return snapshotCount;
    }
}

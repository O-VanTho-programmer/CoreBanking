package com.cbs.lending.service;

import com.cbs.admin.service.WorkingDateService;
import com.cbs.eod.domain.LoanBalanceBucket;
import com.cbs.eod.repository.LoanBalanceBucketRepository;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanBalanceSnapshotServiceTest {

    @Mock
    private LoanAccountRepository loanAccountRepository;

    @Mock
    private LoanScheduleRepository loanScheduleRepository;

    @Mock
    private LoanBalanceBucketRepository loanBalanceBucketRepository;

    @Mock
    private WorkingDateService workingDateService;

    @InjectMocks
    private LoanBalanceSnapshotService loanBalanceSnapshotService;

    private final LocalDate workingDate = LocalDate.of(2026, 12, 1);

    @Test
    @DisplayName("Snapshot: Skips APPROVED loans that have not been disbursed")
    void testSkipUndisbursedLoans() {
        LoanAccount undisbursed = LoanAccount.builder()
                .loanId(101L)
                .loanStatus(LoanStatus.APPROVED.name())
                .disbursedAt(null)
                .build();

        when(loanAccountRepository.findAll()).thenReturn(List.of(undisbursed));

        int count = loanBalanceSnapshotService.takeDailyLoanSnapshot(workingDate);

        assertThat(count).isEqualTo(0);
        verify(loanBalanceBucketRepository, never()).save(any());
    }

    @Test
    @DisplayName("Snapshot: Correctly splits in-term and overdue buckets for active loan with past due schedules")
    void testActiveLoanWithOverdueSchedules() {
        LoanAccount activeLoan = LoanAccount.builder()
                .loanId(202L)
                .loanAccountNumber("0012111000000202")
                .loanStatus(LoanStatus.ACTIVE.name())
                .disbursedAt(OffsetDateTime.now().minusMonths(2))
                .remainingPrincipal(new BigDecimal("45000000.0000"))
                .daysPastDue(15)
                .loanGroup(2)
                .generalProvisionAmount(new BigDecimal("225000.0000"))
                .specificProvisionAmount(new BigDecimal("2250000.0000"))
                .build();

        // Schedule 1: Due Nov 1 (past due relative to Dec 1)
        LoanSchedule pastDueSched = LoanSchedule.builder()
                .scheduleId(1001L)
                .loanId(202L)
                .periodNumber(1)
                .dueDate(LocalDate.of(2026, 11, 1))
                .principalDue(new BigDecimal("5000000.0000"))
                .principalPaid(BigDecimal.ZERO)
                .interestDue(new BigDecimal("500000.0000"))
                .interestPaid(BigDecimal.ZERO)
                .penaltyDue(new BigDecimal("50000.0000"))
                .penaltyPaid(BigDecimal.ZERO)
                .build();

        // Schedule 2: Due Dec 1 (due today)
        LoanSchedule dueTodaySched = LoanSchedule.builder()
                .scheduleId(1002L)
                .loanId(202L)
                .periodNumber(2)
                .dueDate(LocalDate.of(2026, 12, 1))
                .principalDue(new BigDecimal("5000000.0000"))
                .principalPaid(BigDecimal.ZERO)
                .interestDue(new BigDecimal("450000.0000"))
                .interestPaid(BigDecimal.ZERO)
                .penaltyDue(BigDecimal.ZERO)
                .penaltyPaid(BigDecimal.ZERO)
                .build();

        when(loanAccountRepository.findAll()).thenReturn(List.of(activeLoan));
        when(loanScheduleRepository.findDueSchedulesByLoanIdOnOrBefore(202L, workingDate))
                .thenReturn(List.of(pastDueSched, dueTodaySched));

        int count = loanBalanceSnapshotService.takeDailyLoanSnapshot(workingDate);

        assertThat(count).isEqualTo(1);

        ArgumentCaptor<LoanBalanceBucket> captor = ArgumentCaptor.forClass(LoanBalanceBucket.class);
        verify(loanBalanceBucketRepository).save(captor.capture());

        LoanBalanceBucket bucket = captor.getValue();
        assertThat(bucket.getId().getLoanId()).isEqualTo(202L);
        assertThat(bucket.getId().getWorkingDate()).isEqualTo(workingDate);
        assertThat(bucket.getOutstandingPrincipal()).isEqualByComparingTo("45000000.0000");
        // Past due principal = 5,000,000 (from period 1 due Nov 1)
        assertThat(bucket.getPastDuePrincipal()).isEqualByComparingTo("5000000.0000");
        // In-term principal = 45M - 5M = 40M
        assertThat(bucket.getInTermPrincipal()).isEqualByComparingTo("40000000.0000");
        // Overdue interest = 500,000 (period 1)
        assertThat(bucket.getOverdueInterest()).isEqualByComparingTo("500000.0000");
        // In-term accrued interest = 450,000 (period 2 due today)
        assertThat(bucket.getAccruedInterest()).isEqualByComparingTo("450000.0000");
        // Penalty due = 50,000
        assertThat(bucket.getPenaltyDue()).isEqualByComparingTo("50000.0000");
        assertThat(bucket.getLoanGroup()).isEqualTo(2);
        assertThat(bucket.getDaysPastDue()).isEqualTo(15);
        assertThat(bucket.getGeneralProvision()).isEqualByComparingTo("225000.0000");
        assertThat(bucket.getSpecificProvision()).isEqualByComparingTo("2250000.0000");
    }

    @Test
    @DisplayName("Snapshot: Fallback to WorkingDateService when workingDate is null")
    void testFallbackWorkingDate() {
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(loanAccountRepository.findAll()).thenReturn(List.of());

        int count = loanBalanceSnapshotService.takeDailyLoanSnapshot(null);

        assertThat(count).isEqualTo(0);
        verify(workingDateService).getCurrentWorkingDate();
    }
}

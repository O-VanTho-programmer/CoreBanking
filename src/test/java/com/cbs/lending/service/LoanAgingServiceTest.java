package com.cbs.lending.service;

import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.LoanAgingBatchResult;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanAgingServiceTest {

    @Mock
    private LoanAccountRepository loanAccountRepository;

    @Mock
    private LoanScheduleRepository loanScheduleRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoanAgingService loanAgingService;

    private LoanAccount loan;
    private final LocalDate workingDate = LocalDate.of(2026, 12, 1);

    @BeforeEach
    void setUp() {
        loan = LoanAccount.builder()
                .loanId(101L)
                .loanAccountNumber("LN00000000000101")
                .customerId(1001L)
                .linkedCasaAccountId(2001L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .remainingPrincipal(new BigDecimal("50000000.0000"))
                .loanStatus(LoanStatus.ACTIVE.name())
                .loanGroup(1)
                .daysPastDue(0)
                .generalProvisionAmount(BigDecimal.ZERO)
                .specificProvisionAmount(BigDecimal.ZERO)
                .build();

        lenient().when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(999L);
        lenient().when(trxJournalActiveRepository.saveAndFlush(any())).thenAnswer(i -> {
            TrxJournalActive j = i.getArgument(0);
            return TrxJournalActive.builder()
                    .trxId(888L)
                    .trxNumber(j.getTrxNumber())
                    .trxCode(j.getTrxCode())
                    .build();
        });
    }

    @Test
    @DisplayName("Should classify as Group 1 and post 0.75% General Provision when DPD <= 9")
    void testGroup1AgingAndGeneralProvisioning() {
        when(loanAccountRepository.findActiveOrOverdueLoans()).thenReturn(List.of(loan));
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));
        // No past-due schedules
        when(loanScheduleRepository.findDueSchedulesByLoanIdOnOrBefore(101L, workingDate)).thenReturn(List.of());

        PostingResult dummyResult = new PostingResult(
                "LOAN_PROVISION_GEN", workingDate, "VND",
                new BigDecimal("375000.0000"), new BigDecimal("375000.0000"),
                List.of(
                        new JournalLeg(1, 1, null, 101L, "8822", "D", new BigDecimal("375000.0000"), "VND", workingDate, "General Exp"),
                        new JournalLeg(1, 2, null, 101L, "2191", "C", new BigDecimal("375000.0000"), "VND", workingDate, "General Fund")
                )
        );
        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(dummyResult);

        LoanAgingBatchResult result = loanAgingService.executeAgingAndProvisioning(workingDate);

        assertThat(result.totalLoansEvaluated()).isEqualTo(1);
        assertThat(result.overdueLoansCount()).isEqualTo(0);
        assertThat(result.totalGeneralProvisionCalculated()).isEqualByComparingTo("375000.0000");
        assertThat(result.totalSpecificProvisionCalculated()).isEqualByComparingTo(BigDecimal.ZERO);

        assertThat(loan.getLoanGroup()).isEqualTo(1);
        assertThat(loan.getDaysPastDue()).isEqualTo(0);
        assertThat(loan.getGeneralProvisionAmount()).isEqualByComparingTo("375000.0000");
        assertThat(loan.getSpecificProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo("375000.0000");

        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngineService).generateAndValidateEntries(captor.capture());
        assertThat(captor.getValue().trxCode()).isEqualTo("LOAN_PROVISION_GEN");
        assertThat(captor.getValue().trxAmount()).isEqualByComparingTo("375000.0000");
    }

    @Test
    @DisplayName("Should classify as Group 2, transfer overdue principal to GL 2112, and post 5% Specific Provision when DPD = 15")
    void testGroup2AgingOverdueTransferAndSpecificProvision() {
        // Due date was 15 days ago: Nov 16, 2026
        LocalDate pastDueDate = workingDate.minusDays(15);
        LoanSchedule overdueSchedule = LoanSchedule.builder()
                .scheduleId(501L)
                .loanId(101L)
                .periodNumber(1)
                .dueDate(pastDueDate)
                .principalDue(new BigDecimal("5000000.0000"))
                .principalPaid(BigDecimal.ZERO)
                .interestDue(new BigDecimal("500000.0000"))
                .interestPaid(BigDecimal.ZERO)
                .status(LoanScheduleStatus.OVERDUE.name())
                .build();

        // Already has 375,000 general provision
        loan.setGeneralProvisionAmount(new BigDecimal("375000.0000"));

        when(loanAccountRepository.findActiveOrOverdueLoans()).thenReturn(List.of(loan));
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));
        when(loanScheduleRepository.findDueSchedulesByLoanIdOnOrBefore(101L, workingDate))
                .thenReturn(List.of(overdueSchedule));

        PostingResult dummyResult = new PostingResult(
                "MOCK", workingDate, "VND",
                new BigDecimal("5000000.0000"), new BigDecimal("5000000.0000"),
                List.of(new JournalLeg(1, 1, null, 101L, "2112", "D", new BigDecimal("5000000.0000"), "VND", workingDate, "Overdue"))
        );
        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(dummyResult);

        LoanAgingBatchResult result = loanAgingService.executeAgingAndProvisioning(workingDate);

        assertThat(result.overdueLoansCount()).isEqualTo(1);
        assertThat(result.classifiedGroup2Count()).isEqualTo(1);
        assertThat(result.totalOverduePrincipalTransferred()).isEqualByComparingTo("5000000.0000");
        // Specific provision: 5% of 50M = 2,500,000
        assertThat(result.totalSpecificProvisionCalculated()).isEqualByComparingTo("2500000.0000");

        assertThat(loan.getDaysPastDue()).isEqualTo(15);
        assertThat(loan.getLoanGroup()).isEqualTo(2);
        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.OVERDUE.name());
        assertThat(loan.getSpecificProvisionAmount()).isEqualByComparingTo("2500000.0000");
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo("2875000.0000"); // 375k + 2.5M

        // Verify postings: 1 overdue transfer + 1 specific provision
        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngineService, times(2)).generateAndValidateEntries(captor.capture());
        List<PostingRequest> requests = captor.getAllValues();

        assertThat(requests.get(0).trxCode()).isEqualTo("LOAN_OVERDUE_TRANSFER");
        assertThat(requests.get(0).trxAmount()).isEqualByComparingTo("5000000.0000");

        assertThat(requests.get(1).trxCode()).isEqualTo("LOAN_PROVISION_SPEC");
        assertThat(requests.get(1).trxAmount()).isEqualByComparingTo("2500000.0000");
    }

    @Test
    @DisplayName("Should release both General and Specific Provisions to Income 7911 when loan is PAID_OFF")
    void testReleaseProvisionsOnPaidOff() {
        loan.setGeneralProvisionAmount(new BigDecimal("375000.0000"));
        loan.setSpecificProvisionAmount(new BigDecimal("2500000.0000"));

        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(loan));

        PostingResult dummyResult = new PostingResult(
                "RELEASE", workingDate, "VND",
                new BigDecimal("2500000.0000"), new BigDecimal("2500000.0000"),
                List.of(new JournalLeg(1, 1, null, 101L, "2192", "D", new BigDecimal("2500000.0000"), "VND", workingDate, "Release"))
        );
        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(dummyResult);

        loanAgingService.releaseProvisionsOnPaidOff(101L, workingDate);

        assertThat(loan.getGeneralProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getSpecificProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(loan.getTotalProvisionAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        ArgumentCaptor<PostingRequest> captor = ArgumentCaptor.forClass(PostingRequest.class);
        verify(postingEngineService, times(2)).generateAndValidateEntries(captor.capture());
        List<PostingRequest> requests = captor.getAllValues();

        // 1. Release Specific Provision via LOAN_PROVISION_RELEASE (DR 2192 / CR 7911)
        assertThat(requests.get(0).trxCode()).isEqualTo("LOAN_PROVISION_RELEASE");
        assertThat(requests.get(0).trxAmount()).isEqualByComparingTo("2500000.0000");

        // 2. Release General Provision via LOAN_PROV_GEN_RELEASE (DR 2191 / CR 7911)
        assertThat(requests.get(1).trxCode()).isEqualTo("LOAN_PROV_GEN_RELEASE");
        assertThat(requests.get(1).trxAmount()).isEqualByComparingTo("375000.0000");

        verify(loanAccountRepository).save(loan);
    }
}

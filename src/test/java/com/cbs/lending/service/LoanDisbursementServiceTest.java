package com.cbs.lending.service;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.InvalidLoanParameterException;
import com.cbs.common.exception.LoanAccountNotFoundException;
import com.cbs.common.exception.LoanAlreadyDisbursedException;
import com.cbs.common.exception.LoanNotApprovedException;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.DisburseLoanCommand;
import com.cbs.lending.dto.LoanDisbursementReceipt;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.service.IdempotencyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanDisbursementServiceTest {

    @Mock
    private LoanAccountRepository loanAccountRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoanDisbursementService loanDisbursementService;

    private LoanAccount approvedLoan;
    private Account activeCasa;
    private final LocalDate workingDate = LocalDate.of(2026, 11, 4);

    @BeforeEach
    void setUp() {
        approvedLoan = LoanAccount.builder()
                .loanId(101L)
                .loanAccountNumber("0012111000000101")
                .customerId(1001L)
                .linkedCasaAccountId(2001L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .remainingPrincipal(new BigDecimal("50000000.0000"))
                .annualInterestRate(new BigDecimal("0.0950"))
                .overdueInterestRate(new BigDecimal("0.1425"))
                .termMonths(12)
                .repaymentType("ANNUITY_EMI")
                .loanStatus(LoanStatus.APPROVED.name())
                .build();

        activeCasa = Account.builder()
                .accountId(2001L)
                .accountNumber("0014210000002001")
                .customerId(1001L)
                .glCode("4211")
                .currency("VND")
                .branchCode("VN001")
                .ledgerBalance(new BigDecimal("500000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Disbursement: Successful loan disbursement into CASA with balanced journal entries")
    void testDisburseLoanSuccess() {
        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-001")
                .makerId("TELLER01")
                .narrationOverride("Giai ngan hop dong tin dung")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-001")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(false);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);

        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(approvedLoan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(activeCasa));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(9999L);

        JournalLeg leg1 = new JournalLeg(1, 1, null, 101L, "2111", "D",
                new BigDecimal("50000000.0000"), "VND", workingDate, "Giai ngan khoan vay tin dung");
        JournalLeg leg2 = new JournalLeg(1, 2, 2001L, null, "4211", "C",
                new BigDecimal("50000000.0000"), "VND", workingDate, "Giai ngan vao tai khoan thanh toan CASA");

        PostingResult postingResult = new PostingResult("LOAN_DISBURSE", workingDate, "VND",
                new BigDecimal("50000000.0000"), new BigDecimal("50000000.0000"), List.of(leg1, leg2));
        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);

        when(trxJournalActiveRepository.saveAndFlush(any(TrxJournalActive.class))).thenAnswer(invocation -> {
            TrxJournalActive j = invocation.getArgument(0);
            j.setTrxId(777L);
            return j;
        });

        LoanDisbursementReceipt receipt = loanDisbursementService.disburse(cmd);

        assertThat(receipt).isNotNull();
        assertThat(receipt.trxId()).isEqualTo(777L);
        assertThat(receipt.trxNumber()).isEqualTo("TRX-20261104-00009999");
        assertThat(receipt.loanId()).isEqualTo(101L);
        assertThat(receipt.disbursedAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(receipt.casaAccountId()).isEqualTo(2001L);
        assertThat(receipt.casaAvailableBalance()).isEqualByComparingTo("50500000.0000"); // 500k + 50M
        assertThat(receipt.status()).isEqualTo("COMPLETED");

        // Verify balance mutation
        assertThat(activeCasa.getLedgerBalance()).isEqualByComparingTo("50500000.0000");
        assertThat(activeCasa.getAvailableBalance()).isEqualByComparingTo("50500000.0000");
        verify(accountRepository).save(activeCasa);

        // Verify loan status transition
        assertThat(approvedLoan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE.name());
        assertThat(approvedLoan.getDisbursedAt()).isNotNull();
        verify(loanAccountRepository).save(approvedLoan);

        // Verify journal entry persistence
        verify(journalEntryActiveRepository, times(2)).save(any(JournalEntryActive.class));
        verify(idempotencyService).recordCompletion(eq("BRANCH"), eq("idemp-disb-001"), any());
    }

    @Test
    @DisplayName("Idempotency Guard: Replays existing receipt without duplicate disbursement")
    void testIdempotencyHitReplaysReceipt() {
        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-001")
                .build();

        TransactionReceipt cachedTrx = TransactionReceipt.builder()
                .trxId(777L)
                .trxNumber("TRX-20261104-00009999")
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-001")
                .trxCode("LOAN_DISBURSE")
                .workingDate(workingDate)
                .status("COMPLETED")
                .currency("VND")
                .totalAmount(new BigDecimal("50000000.0000"))
                .toAccountAvailableBalance(new BigDecimal("50500000.0000"))
                .legs(List.of())
                .timestamp(OffsetDateTime.now())
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-001")).thenReturn(Optional.of(cachedTrx));
        when(loanAccountRepository.findById(101L)).thenReturn(Optional.of(approvedLoan));
        when(accountRepository.findById(2001L)).thenReturn(Optional.of(activeCasa));

        LoanDisbursementReceipt receipt = loanDisbursementService.disburse(cmd);

        assertThat(receipt.trxNumber()).isEqualTo("TRX-20261104-00009999");
        assertThat(receipt.disbursedAmount()).isEqualByComparingTo("50000000.0000");

        // Verify no new transaction was written
        verify(postingEngineService, never()).generateAndValidateEntries(any());
        verify(loanAccountRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("Cutoff Guard: Rejects disbursement when system is in CUTOFF state")
    void testCutoffGuardBlocksDisbursement() {
        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-002")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-002")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(true);

        assertThatThrownBy(() -> loanDisbursementService.disburse(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("CUTOFF state");
    }

    @Test
    @DisplayName("Validation: Rejects disbursement if loan is already in ACTIVE status")
    void testLoanAlreadyDisbursedRejection() {
        approvedLoan.setLoanStatus(LoanStatus.ACTIVE.name());

        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-003")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-003")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(false);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(approvedLoan));

        assertThatThrownBy(() -> loanDisbursementService.disburse(cmd))
                .isInstanceOf(LoanAlreadyDisbursedException.class)
                .hasMessageContaining("already in 'ACTIVE' status");
    }

    @Test
    @DisplayName("Validation: Rejects disbursement if loan is not in APPROVED status")
    void testLoanNotApprovedRejection() {
        approvedLoan.setLoanStatus("OVERDUE");

        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-004")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-004")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(false);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(approvedLoan));

        assertThatThrownBy(() -> loanDisbursementService.disburse(cmd))
                .isInstanceOf(LoanNotApprovedException.class)
                .hasMessageContaining("not in APPROVED status");
    }

    @Test
    @DisplayName("Validation: Rejects disbursement if linked CASA does not belong to borrower")
    void testCasaCustomerMismatchRejection() {
        activeCasa.setCustomerId(9999L); // different customer

        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-005")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-005")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(false);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(approvedLoan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(activeCasa));

        assertThatThrownBy(() -> loanDisbursementService.disburse(cmd))
                .isInstanceOf(InvalidLoanParameterException.class)
                .hasMessageContaining("does not belong to borrower customer");
    }

    @Test
    @DisplayName("Validation: Rejects disbursement if linked CASA is not ACTIVE")
    void testCasaNotActiveRejection() {
        activeCasa.setStatus("BLOCKED");

        DisburseLoanCommand cmd = DisburseLoanCommand.builder()
                .loanId(101L)
                .channelId("BRANCH")
                .idempotencyKey("idemp-disb-006")
                .build();

        when(idempotencyService.checkExisting("BRANCH", "idemp-disb-006")).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(false);
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(loanAccountRepository.findByIdForUpdate(101L)).thenReturn(Optional.of(approvedLoan));
        when(accountRepository.findByIdForUpdate(2001L)).thenReturn(Optional.of(activeCasa));

        assertThatThrownBy(() -> loanDisbursementService.disburse(cmd))
                .isInstanceOf(InvalidLoanParameterException.class)
                .hasMessageContaining("is not ACTIVE");
    }
}

package com.cbs.lending.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.AutoRepaymentBatchResult;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Phase 4c: EOD Automated Loan Repayment & Waterfall Allocation Service.
 *
 * Implements:
 * - Priority Waterfall Allocation: Penalty -> Overdue/Normal Interest -> Normal Principal
 * - Double-Entry Ledger Posting: LOAN_REPAY (DR 4211 CASA / CR 2111 Principal, CR 7020 Interest)
 * - Balance Integrity: CASA Available = Ledger - Hold
 * - Schedule & Account State Transitions: PAID, PARTIALLY_PAID, OVERDUE, PAID_OFF
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanRepaymentService {

    private final LoanScheduleRepository loanScheduleRepository;
    private final LoanAccountRepository loanAccountRepository;
    private final AccountRepository accountRepository;
    private final PostingEngineService postingEngineService;
    private final WorkingDateService workingDateService;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes automated EOD loan repayment for all due schedules on or before the given working date.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AutoRepaymentBatchResult executeAutoRepayment(LocalDate workingDate) {
        LocalDate effectiveDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting EOD Auto-Repayment batch for working date: {}", effectiveDate);

        List<LoanSchedule> dueSchedules = loanScheduleRepository.findDueSchedulesOnOrBefore(effectiveDate);
        log.info("Found {} due/unpaid loan schedules eligible for auto-repayment", dueSchedules.size());

        int fullyPaidCount = 0;
        int partiallyPaidCount = 0;
        int fullyPaidOffLoansCount = 0;
        BigDecimal sumPrincipal = BigDecimal.ZERO;
        BigDecimal sumInterest = BigDecimal.ZERO;
        BigDecimal sumPenalty = BigDecimal.ZERO;

        for (LoanSchedule schedule : dueSchedules) {
            // 1. Lock LoanAccount
            LoanAccount loan = loanAccountRepository.findByIdForUpdate(schedule.getLoanId())
                    .orElse(null);
            if (loan == null || LoanStatus.PAID_OFF.name().equals(loan.getLoanStatus())) {
                continue;
            }

            // 2. Lock Linked CASA Account
            Account casaAccount = accountRepository.findByIdForUpdate(loan.getLinkedCasaAccountId())
                    .orElse(null);
            if (casaAccount == null || !"ACTIVE".equals(casaAccount.getStatus())) {
                if (schedule.getDueDate().isBefore(effectiveDate)) {
                    schedule.setStatus(LoanScheduleStatus.OVERDUE.name());
                    loanScheduleRepository.save(schedule);
                }
                continue;
            }

            // 3. Calculate Outstanding Dues on this Schedule
            BigDecimal penaltyDue = schedule.getPenaltyDue().subtract(schedule.getPenaltyPaid()).max(BigDecimal.ZERO);
            BigDecimal interestDue = schedule.getInterestDue().subtract(schedule.getInterestPaid()).max(BigDecimal.ZERO);
            BigDecimal principalDue = schedule.getPrincipalDue().subtract(schedule.getPrincipalPaid()).max(BigDecimal.ZERO);

            BigDecimal available = casaAccount.getAvailableBalance().max(BigDecimal.ZERO);

            if (available.compareTo(BigDecimal.ZERO) <= 0) {
                // CASA has zero balance
                if (schedule.getDueDate().isBefore(effectiveDate)) {
                    schedule.setStatus(LoanScheduleStatus.OVERDUE.name());
                    loanScheduleRepository.save(schedule);
                }
                continue;
            }

            // 4. Waterfall Allocation: Penalty -> Interest -> Principal
            BigDecimal pPay = penaltyDue.min(available);
            available = available.subtract(pPay);

            BigDecimal iPay = interestDue.min(available);
            available = available.subtract(iPay);

            BigDecimal prPay = principalDue.min(available);

            BigDecimal totalDeduction = pPay.add(iPay).add(prPay);
            if (totalDeduction.compareTo(BigDecimal.ZERO) <= 0) {
                if (schedule.getDueDate().isBefore(effectiveDate)) {
                    schedule.setStatus(LoanScheduleStatus.OVERDUE.name());
                    loanScheduleRepository.save(schedule);
                }
                continue;
            }

            // 5. Post LOAN_REPAY transaction via Posting Engine
            String currency = casaAccount.getCurrency() != null ? casaAccount.getCurrency() : "VND";
            PostingRequest request = PostingRequest.builder()
                    .trxCode("LOAN_REPAY")
                    .currency(currency)
                    .workingDate(effectiveDate)
                    .trxAmount(totalDeduction)
                    .principalAmount(prPay)
                    .interestAmount(iPay)
                    .penaltyAmount(pPay)
                    .customerAccountId(casaAccount.getAccountId())
                    .customerGlCode(casaAccount.getGlCode())
                    .loanId(loan.getLoanId())
                    .branchCode(casaAccount.getBranchCode())
                    .productCode(loan.getProductCode())
                    .narrationOverride(String.format("Trich thu no ky %d HD %s",
                            schedule.getPeriodNumber(), loan.getLoanAccountNumber()))
                    .build();

            PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);

            // 6. Mutate CASA Account Balance
            casaAccount.setLedgerBalance(casaAccount.getLedgerBalance().subtract(totalDeduction));
            accountRepository.save(casaAccount);

            // 7. Update Loan Schedule
            schedule.setPenaltyPaid(schedule.getPenaltyPaid().add(pPay));
            schedule.setInterestPaid(schedule.getInterestPaid().add(iPay));
            schedule.setPrincipalPaid(schedule.getPrincipalPaid().add(prPay));

            boolean scheduleFullyPaid = schedule.getPrincipalPaid().compareTo(schedule.getPrincipalDue()) >= 0
                    && schedule.getInterestPaid().compareTo(schedule.getInterestDue()) >= 0;

            if (scheduleFullyPaid) {
                schedule.setStatus(LoanScheduleStatus.PAID.name());
                fullyPaidCount++;
            } else {
                if (schedule.getDueDate().isBefore(effectiveDate)) {
                    schedule.setStatus(LoanScheduleStatus.OVERDUE.name());
                } else {
                    schedule.setStatus(LoanScheduleStatus.PARTIALLY_PAID.name());
                }
                partiallyPaidCount++;
            }
            schedule.setUpdatedAt(OffsetDateTime.now());
            loanScheduleRepository.save(schedule);

            // 8. Update Loan Account Remaining Principal
            loan.setRemainingPrincipal(loan.getRemainingPrincipal().subtract(prPay).max(BigDecimal.ZERO));
            if (loan.getRemainingPrincipal().compareTo(BigDecimal.ZERO) == 0) {
                loan.setLoanStatus(LoanStatus.PAID_OFF.name());
                fullyPaidOffLoansCount++;
                log.info("Loan contract {} fully PAID_OFF!", loan.getLoanAccountNumber());
            }
            loan.setUpdatedAt(OffsetDateTime.now());
            loanAccountRepository.save(loan);

            // 9. Persist Journal Entries
            persistRepaymentJournal(effectiveDate, schedule, loan, postingResult);

            sumPrincipal = sumPrincipal.add(prPay);
            sumInterest = sumInterest.add(iPay);
            sumPenalty = sumPenalty.add(pPay);
        }

        log.info("EOD Auto-Repayment batch completed: processed={}, fullyPaid={}, partial={}, totalPrin={}, totalInt={}",
                dueSchedules.size(), fullyPaidCount, partiallyPaidCount, sumPrincipal, sumInterest);

        return AutoRepaymentBatchResult.builder()
                .workingDate(effectiveDate)
                .totalDueSchedulesProcessed(dueSchedules.size())
                .fullyPaidSchedulesCount(fullyPaidCount)
                .partiallyPaidSchedulesCount(partiallyPaidCount)
                .fullyPaidOffLoansCount(fullyPaidOffLoansCount)
                .totalPrincipalCollected(sumPrincipal)
                .totalInterestCollected(sumInterest)
                .totalPenaltyCollected(sumPenalty)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    private void persistRepaymentJournal(LocalDate workingDate, LoanSchedule schedule,
                                         LoanAccount loan, PostingResult postingResult) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        String dateStr = workingDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String trxNumber = String.format("TRX-%s-%08d", dateStr, seq);
        OffsetDateTime now = OffsetDateTime.now();

        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId("SYSTEM_BATCH")
                .idempotencyKey(String.format("LOAN_REPAY:%d:%d:%s-%d", loan.getLoanId(), schedule.getScheduleId(), workingDate, seq))
                .trxCode("LOAN_REPAY")
                .workingDate(workingDate)
                .status("COMPLETED")
                .makerId("BATCH_EOD")
                .isReversed("N")
                .createdAt(now)
                .build();
        journal = trxJournalActiveRepository.saveAndFlush(journal);

        for (JournalLeg leg : postingResult.legs()) {
            JournalEntryActive entry = JournalEntryActive.builder()
                    .trxId(journal.getTrxId())
                    .entryGroup(leg.entryGroup())
                    .entryIndex(leg.entryIndex())
                    .accountId(leg.accountId())
                    .loanId(leg.loanId())
                    .glCode(leg.glCode())
                    .drCr(leg.drCr())
                    .amount(leg.amount())
                    .currency(leg.currency())
                    .valueDate(leg.valueDate())
                    .createdAt(now)
                    .build();
            journalEntryActiveRepository.save(entry);
        }
    }
}

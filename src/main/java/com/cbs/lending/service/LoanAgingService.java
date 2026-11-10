package com.cbs.lending.service;

import com.cbs.depositing.dto.JournalLeg;
import com.cbs.depositing.dto.PostingRequest;
import com.cbs.depositing.dto.PostingResult;
import com.cbs.depositing.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.LoanAgingBatchResult;
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
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Phase 4c: EOD Loan Classification, Overdue Aging, and Provisioning Service.
 *
 * Implements:
 * 1. DPD Calculation and Loan Group Classification (Circular 11/2021/TT-NHNN)
 * 2. Overdue Principal Transfer to GL 2112 (LOAN_OVERDUE_TRANSFER)
 * 3. General (0.75%) & Specific Provisioning (LOAN_PROVISION: DR 8821/8822, CR 2191/2192)
 * 4. Provision Reversal upon Paid Off (LOAN_PROVISION_RELEASE: DR 2192, CR 7911 SYS_PROV_REVERSAL_INCOME)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanAgingService {

    private final LoanAccountRepository loanAccountRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final PostingEngineService postingEngineService;
    private final WorkingDateService workingDateService;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final JdbcTemplate jdbcTemplate;

    private static final BigDecimal GENERAL_PROVISION_RATE = new BigDecimal("0.0075"); // 0.75%

    /**
     * Executes loan aging, classification, overdue GL transfer, and provisioning for EOD.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanAgingBatchResult executeAgingAndProvisioning(LocalDate workingDate) {
        LocalDate effectiveDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting EOD Loan Aging & Provisioning batch for working date: {}", effectiveDate);

        List<LoanAccount> loans = loanAccountRepository.findActiveOrOverdueLoans();
        log.info("Evaluating {} active/overdue loans for aging and provisioning", loans.size());

        int overdueCount = 0;
        int group2Count = 0;
        int group3To5Count = 0;
        BigDecimal sumOverdueTransfer = BigDecimal.ZERO;
        BigDecimal sumGeneralProvision = BigDecimal.ZERO;
        BigDecimal sumSpecificProvision = BigDecimal.ZERO;
        BigDecimal sumProvisionReleased = BigDecimal.ZERO;

        for (LoanAccount loan : loans) {
            LoanAccount lockedLoan = loanAccountRepository.findByIdForUpdate(loan.getLoanId()).orElse(null);
            if (lockedLoan == null) {
                continue;
            }

            // 1. Calculate Days Past Due (DPD) from past-due schedules
            List<LoanSchedule> dueSchedules = loanScheduleRepository
                    .findDueSchedulesByLoanIdOnOrBefore(lockedLoan.getLoanId(), effectiveDate);

            List<LoanSchedule> pastDueSchedules = dueSchedules.stream()
                    .filter(s -> s.getDueDate().isBefore(effectiveDate))
                    .toList();

            int dpd = 0;
            if (!pastDueSchedules.isEmpty()) {
                LocalDate earliestPastDueDate = pastDueSchedules.stream()
                        .map(LoanSchedule::getDueDate)
                        .min(LocalDate::compareTo)
                        .orElse(effectiveDate);
                dpd = (int) ChronoUnit.DAYS.between(earliestPastDueDate, effectiveDate);
            }

            lockedLoan.setDaysPastDue(dpd);

            // 2. Classify Loan Group according to Circular 11/2021/TT-NHNN
            int group = classifyGroup(dpd);
            lockedLoan.setLoanGroup(group);

            if (group >= 2) {
                if (group == 2) group2Count++;
                else group3To5Count++;
            }

            // 3. Status Transition & Overdue Principal Transfer
            if (dpd > 0) {
                overdueCount++;
                String previousStatus = lockedLoan.getLoanStatus();
                lockedLoan.setLoanStatus(LoanStatus.OVERDUE.name());

                if (!LoanStatus.OVERDUE.name().equals(previousStatus)) {
                    // First time becoming overdue: transfer past-due principal to GL 2112
                    BigDecimal overduePrincipal = pastDueSchedules.stream()
                            .map(s -> s.getPrincipalDue().subtract(s.getPrincipalPaid()).max(BigDecimal.ZERO))
                            .reduce(BigDecimal.ZERO, BigDecimal::add);

                    if (overduePrincipal.compareTo(BigDecimal.ZERO) > 0) {
                        postOverdueTransfer(effectiveDate, lockedLoan, overduePrincipal);
                        sumOverdueTransfer = sumOverdueTransfer.add(overduePrincipal);
                    }
                }
            } else {
                if (LoanStatus.OVERDUE.name().equals(lockedLoan.getLoanStatus())) {
                    lockedLoan.setLoanStatus(LoanStatus.ACTIVE.name());
                }
            }

            // 4. Calculate General and Specific Provisions
            BigDecimal remainingPrincipal = lockedLoan.getRemainingPrincipal();
            if (remainingPrincipal.compareTo(BigDecimal.ZERO) > 0) {
                // 4A. General Provision: 0.75% for Groups 1-4, 0% for Group 5
                BigDecimal targetGen = (group <= 4)
                        ? remainingPrincipal.multiply(GENERAL_PROVISION_RATE).setScale(4, RoundingMode.HALF_UP)
                        : BigDecimal.ZERO;

                BigDecimal deltaGen = targetGen.subtract(lockedLoan.getGeneralProvisionAmount());
                if (deltaGen.compareTo(BigDecimal.ZERO) > 0) {
                    postGeneralProvision(effectiveDate, lockedLoan, deltaGen);
                    sumGeneralProvision = sumGeneralProvision.add(deltaGen);
                    lockedLoan.setGeneralProvisionAmount(targetGen);
                } else if (deltaGen.compareTo(BigDecimal.ZERO) < 0) {
                    BigDecimal releaseGen = deltaGen.abs();
                    postGeneralProvisionRelease(effectiveDate, lockedLoan, releaseGen);
                    sumProvisionReleased = sumProvisionReleased.add(releaseGen);
                    lockedLoan.setGeneralProvisionAmount(targetGen);
                }

                // 4B. Specific Provision: 0% G1, 5% G2, 20% G3, 50% G4, 100% G5
                BigDecimal specRate = getSpecificProvisionRate(group);
                BigDecimal targetSpec = remainingPrincipal.multiply(specRate).setScale(4, RoundingMode.HALF_UP);

                BigDecimal deltaSpec = targetSpec.subtract(lockedLoan.getSpecificProvisionAmount());
                if (deltaSpec.compareTo(BigDecimal.ZERO) > 0) {
                    postSpecificProvision(effectiveDate, lockedLoan, deltaSpec);
                    sumSpecificProvision = sumSpecificProvision.add(deltaSpec);
                    lockedLoan.setSpecificProvisionAmount(targetSpec);
                } else if (deltaSpec.compareTo(BigDecimal.ZERO) < 0) {
                    // Release excess specific provision
                    BigDecimal releaseAmount = deltaSpec.abs();
                    postSpecificProvisionRelease(effectiveDate, lockedLoan, releaseAmount);
                    sumProvisionReleased = sumProvisionReleased.add(releaseAmount);
                    lockedLoan.setSpecificProvisionAmount(targetSpec);
                }
            }

            lockedLoan.setUpdatedAt(OffsetDateTime.now());
            loanAccountRepository.save(lockedLoan);
        }

        log.info("EOD Loan Aging & Provisioning completed: evaluated={}, overdue={}, genProv={}, specProv={}, provRel={}",
                loans.size(), overdueCount, sumGeneralProvision, sumSpecificProvision, sumProvisionReleased);

        return LoanAgingBatchResult.builder()
                .workingDate(effectiveDate)
                .totalLoansEvaluated(loans.size())
                .overdueLoansCount(overdueCount)
                .classifiedGroup2Count(group2Count)
                .classifiedGroup3To5Count(group3To5Count)
                .totalOverduePrincipalTransferred(sumOverdueTransfer)
                .totalGeneralProvisionCalculated(sumGeneralProvision)
                .totalSpecificProvisionCalculated(sumSpecificProvision)
                .totalProvisionReleased(sumProvisionReleased)
                .completedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * Releases all provisions when a loan is fully PAID_OFF.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public void releaseProvisionsOnPaidOff(Long loanId, LocalDate workingDate) {
        LoanAccount loan = loanAccountRepository.findByIdForUpdate(loanId).orElse(null);
        if (loan == null) return;

        LocalDate effectiveDate = (workingDate != null) ? workingDate : workingDateService.getCurrentWorkingDate();

        if (loan.getSpecificProvisionAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal releaseAmount = loan.getSpecificProvisionAmount();
            postSpecificProvisionRelease(effectiveDate, loan, releaseAmount);
            loan.setSpecificProvisionAmount(BigDecimal.ZERO);
        }

        if (loan.getGeneralProvisionAmount().compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal releaseGenAmount = loan.getGeneralProvisionAmount();
            postGeneralProvisionRelease(effectiveDate, loan, releaseGenAmount);
            loan.setGeneralProvisionAmount(BigDecimal.ZERO);
        }

        loan.setUpdatedAt(OffsetDateTime.now());
        loanAccountRepository.save(loan);
    }

    private int classifyGroup(int dpd) {
        if (dpd <= 9) return 1;
        if (dpd <= 90) return 2;
        if (dpd <= 180) return 3;
        if (dpd <= 360) return 4;
        return 5;
    }

    private BigDecimal getSpecificProvisionRate(int group) {
        return switch (group) {
            case 2 -> new BigDecimal("0.0500"); // 5%
            case 3 -> new BigDecimal("0.2000"); // 20%
            case 4 -> new BigDecimal("0.5000"); // 50%
            case 5 -> new BigDecimal("1.0000"); // 100%
            default -> BigDecimal.ZERO;         // Group 1: 0%
        };
    }

    private void postOverdueTransfer(LocalDate workingDate, LoanAccount loan, BigDecimal amount) {
        PostingRequest request = PostingRequest.builder()
                .trxCode("LOAN_OVERDUE_TRANSFER")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(amount)
                .loanId(loan.getLoanId())
                .productCode(loan.getProductCode())
                .narrationOverride(String.format("Chuyen no qua han HD %s", loan.getLoanAccountNumber()))
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);
        persistJournal(workingDate, "LOAN_OVERDUE_TRANSFER", loan, postingResult);
    }

    private void postGeneralProvision(LocalDate workingDate, LoanAccount loan, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        PostingRequest request = PostingRequest.builder()
                .trxCode("LOAN_PROVISION_GEN")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(amount)
                .loanId(loan.getLoanId())
                .productCode(loan.getProductCode())
                .narrationOverride(String.format("Trich lap du phong chung HD %s", loan.getLoanAccountNumber()))
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);
        persistJournal(workingDate, "LOAN_PROVISION_GEN", loan, postingResult);
    }

    private void postSpecificProvision(LocalDate workingDate, LoanAccount loan, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        PostingRequest request = PostingRequest.builder()
                .trxCode("LOAN_PROVISION_SPEC")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(amount)
                .loanId(loan.getLoanId())
                .productCode(loan.getProductCode())
                .narrationOverride(String.format("Trich lap du phong cu the HD %s", loan.getLoanAccountNumber()))
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);
        persistJournal(workingDate, "LOAN_PROVISION_SPEC", loan, postingResult);
    }

    private void postSpecificProvisionRelease(LocalDate workingDate, LoanAccount loan, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        PostingRequest request = PostingRequest.builder()
                .trxCode("LOAN_PROVISION_RELEASE")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(amount)
                .loanId(loan.getLoanId())
                .productCode(loan.getProductCode())
                .narrationOverride(String.format("Hoan nhap du phong cu the HD %s", loan.getLoanAccountNumber()))
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);
        persistJournal(workingDate, "LOAN_PROVISION_RELEASE", loan, postingResult);
    }

    private void postGeneralProvisionRelease(LocalDate workingDate, LoanAccount loan, BigDecimal amount) {
        if (amount.compareTo(BigDecimal.ZERO) <= 0) return;

        PostingRequest request = PostingRequest.builder()
                .trxCode("LOAN_PROV_GEN_RELEASE")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(amount)
                .loanId(loan.getLoanId())
                .productCode(loan.getProductCode())
                .narrationOverride(String.format("Hoan nhap du phong chung HD %s", loan.getLoanAccountNumber()))
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(request);
        persistJournal(workingDate, "LOAN_PROV_GEN_RELEASE", loan, postingResult);
    }

    private void persistJournal(LocalDate workingDate, String trxCode, LoanAccount loan, PostingResult postingResult) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        String dateStr = workingDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        String trxNumber = String.format("TRX-%s-%08d", dateStr, seq);
        OffsetDateTime now = OffsetDateTime.now();

        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId("SYSTEM_BATCH")
                .idempotencyKey(String.format("%s:%d:%s-%d", trxCode, loan.getLoanId(), workingDate, seq))
                .trxCode(trxCode)
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

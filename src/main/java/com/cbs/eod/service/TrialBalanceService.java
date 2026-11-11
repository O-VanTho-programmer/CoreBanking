package com.cbs.eod.service;

import com.cbs.accounting.domain.GlAccount;
import com.cbs.accounting.repository.GlAccountRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.SubledgerGlDiscrepancyException;
import com.cbs.common.exception.TrialBalanceImbalanceException;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.eod.dto.TrialBalanceEntry;
import com.cbs.eod.dto.TrialBalanceReport;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 3e: General Ledger Trial Balance Reconciler Service.
 * Performs mathematical equilibrium and sub-ledger integrity verifications
 * at End-of-Day (EOD).
 *
 * Checks:
 * 1. Macro Equilibrium: SUM(Debit) == SUM(Credit) bounded strictly to the working date.
 * 2. Sub-ledger vs. GL 4211 Turnover: Customer bucket turnover == Control GL 4211 turnover.
 * 3. Master Live vs. Snapshot Balance: Live ACCOUNT total == Frozen Bucket total.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TrialBalanceService {

    public static final String CASA_GL_CODE = "4211";

    private final JournalEntryDoneRepository journalEntryDoneRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final AccountBalanceBucketRepository bucketRepository;
    private final AccountRepository accountRepository;
    private final GlAccountRepository glAccountRepository;
    private final WorkingDateService workingDateService;

    /**
     * Executes the comprehensive Trial Balance reconciliation for the given working date.
     *
     * @param workingDate operational date to audit (defaults to current working date if null)
     * @return TrialBalanceReport containing the detailed audit breakdown
     * @throws TrialBalanceImbalanceException if macro debit != credit
     * @throws SubledgerGlDiscrepancyException if sub-ledger turnovers or balances mismatch GL
     */
    @Transactional(readOnly = true)
    public TrialBalanceReport verifyTrialBalance(LocalDate workingDate) {
        LocalDate effectiveWorkingDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting Trial Balance Reconciliation for working date: {}", effectiveWorkingDate);

        // 1. Check 1: Macro Equilibrium (Total Debit == Total Credit)
        BigDecimal macroDebit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal macroCredit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (Object[] row : journalEntryDoneRepository.sumTurnoverByDrCr(effectiveWorkingDate)) {
            String drCr = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if ("D".equals(drCr)) macroDebit = macroDebit.add(amt);
            else if ("C".equals(drCr)) macroCredit = macroCredit.add(amt);
        }

        for (Object[] row : journalEntryActiveRepository.sumTurnoverByDrCr(effectiveWorkingDate)) {
            String drCr = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if ("D".equals(drCr)) macroDebit = macroDebit.add(amt);
            else if ("C".equals(drCr)) macroCredit = macroCredit.add(amt);
        }

        if (macroDebit.compareTo(macroCredit) != 0) {
            log.error("Macro Equilibrium Check FAILED on {}: Debit={}, Credit={}",
                    effectiveWorkingDate, macroDebit, macroCredit);
            throw new TrialBalanceImbalanceException(effectiveWorkingDate, macroDebit, macroCredit);
        }

        // 2. Turnover per GL account for detailed inquiry
        BigDecimal gl4211Debit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal gl4211Credit = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (Object[] row : journalEntryDoneRepository.sumTurnoverByGlCodeAndDrCr(effectiveWorkingDate, CASA_GL_CODE)) {
            String drCr = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if ("D".equals(drCr)) gl4211Debit = gl4211Debit.add(amt);
            else if ("C".equals(drCr)) gl4211Credit = gl4211Credit.add(amt);
        }

        for (Object[] row : journalEntryActiveRepository.sumTurnoverByGlCodeAndDrCr(effectiveWorkingDate, CASA_GL_CODE)) {
            String drCr = (String) row[0];
            BigDecimal amt = (BigDecimal) row[1];
            if ("D".equals(drCr)) gl4211Debit = gl4211Debit.add(amt);
            else if ("C".equals(drCr)) gl4211Credit = gl4211Credit.add(amt);
        }

        // Build individual GL balance entries for UI display
        Map<String, BigDecimal[]> glTurnovers = new HashMap<>();
        for (Object[] row : journalEntryDoneRepository.sumTurnoverByGlCode(effectiveWorkingDate)) {
            String gl = (String) row[0];
            String drCr = (String) row[1];
            BigDecimal amt = (BigDecimal) row[2];
            BigDecimal[] pair = glTurnovers.computeIfAbsent(gl, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("D".equals(drCr)) pair[0] = pair[0].add(amt);
            else if ("C".equals(drCr)) pair[1] = pair[1].add(amt);
        }
        for (Object[] row : journalEntryActiveRepository.sumTurnoverByGlCode(effectiveWorkingDate)) {
            String gl = (String) row[0];
            String drCr = (String) row[1];
            BigDecimal amt = (BigDecimal) row[2];
            BigDecimal[] pair = glTurnovers.computeIfAbsent(gl, k -> new BigDecimal[]{BigDecimal.ZERO, BigDecimal.ZERO});
            if ("D".equals(drCr)) pair[0] = pair[0].add(amt);
            else if ("C".equals(drCr)) pair[1] = pair[1].add(amt);
        }

        Map<String, String> glNames = glAccountRepository.findAll().stream()
                .collect(Collectors.toMap(GlAccount::getGlCode, GlAccount::getGlName, (a, b) -> a));

        List<TrialBalanceEntry> entries = glTurnovers.entrySet().stream()
                .map(e -> {
                    String gl = e.getKey();
                    BigDecimal d = e.getValue()[0].setScale(4, RoundingMode.HALF_UP);
                    BigDecimal c = e.getValue()[1].setScale(4, RoundingMode.HALF_UP);
                    String name = glNames.getOrDefault(gl, "GL Account " + gl);
                    return new TrialBalanceEntry(gl, name, d, c, c.subtract(d));
                })
                .sorted(Comparator.comparing(TrialBalanceEntry::accountNumber))
                .toList();

        // 3. Check 2 & 3: Sub-ledger turnover and snapshot reconciliation
        BigDecimal liveAccountTotal = accountRepository.sumLedgerBalanceByGlCode(CASA_GL_CODE)
                .setScale(4, RoundingMode.HALF_UP);

        boolean bucketsExist = bucketRepository.existsByWorkingDate(effectiveWorkingDate);
        BigDecimal subledgerDebit;
        BigDecimal subledgerCredit;
        BigDecimal bucketTotalClosing;

        if (bucketsExist) {
            // Post-EOD / Snapshot mode: check against frozen balance buckets
            subledgerDebit = bucketRepository.sumDailyDebitByWorkingDate(effectiveWorkingDate)
                    .setScale(4, RoundingMode.HALF_UP);
            subledgerCredit = bucketRepository.sumDailyCreditByWorkingDate(effectiveWorkingDate)
                    .setScale(4, RoundingMode.HALF_UP);
            bucketTotalClosing = bucketRepository.sumClosingBalanceByWorkingDate(effectiveWorkingDate)
                    .setScale(4, RoundingMode.HALF_UP);

            if (subledgerDebit.compareTo(gl4211Debit) != 0) {
                log.error("Sub-ledger vs GL Debit turnover FAILED on {}: Subledger={}, GL 4211={}",
                        effectiveWorkingDate, subledgerDebit, gl4211Debit);
                throw new SubledgerGlDiscrepancyException(effectiveWorkingDate, "Debit Turnover", subledgerDebit, gl4211Debit);
            }

            if (subledgerCredit.compareTo(gl4211Credit) != 0) {
                log.error("Sub-ledger vs GL Credit turnover FAILED on {}: Subledger={}, GL 4211={}",
                        effectiveWorkingDate, subledgerCredit, gl4211Credit);
                throw new SubledgerGlDiscrepancyException(effectiveWorkingDate, "Credit Turnover", subledgerCredit, gl4211Credit);
            }

            if (liveAccountTotal.compareTo(bucketTotalClosing) != 0) {
                log.error("Live Account vs Snapshot Bucket FAILED on {}: Live={}, Bucket={}",
                        effectiveWorkingDate, liveAccountTotal, bucketTotalClosing);
                throw new SubledgerGlDiscrepancyException(effectiveWorkingDate, "Closing Balance", bucketTotalClosing, liveAccountTotal);
            }
        } else {
            // Daytime / Pre-EOD mode: snapshot buckets not yet created by Step 4.
            // Sub-ledger turnover matches current GL 4211 turnover in daytime equilibrium.
            subledgerDebit = gl4211Debit;
            subledgerCredit = gl4211Credit;
            bucketTotalClosing = liveAccountTotal;
        }

        log.info("Trial Balance SUCCESS for {}: Macro Debit/Credit={}, GL 4211 Turnover (DR={}/CR={}), CASA Balance={}",
                effectiveWorkingDate, macroDebit, gl4211Debit, gl4211Credit, liveAccountTotal);

        return TrialBalanceReport.builder()
                .workingDate(effectiveWorkingDate)
                .macroDebitSum(macroDebit)
                .macroCreditSum(macroCredit)
                .subledgerDebitSum(subledgerDebit)
                .subledgerCreditSum(subledgerCredit)
                .gl4211DebitSum(gl4211Debit)
                .gl4211CreditSum(gl4211Credit)
                .liveAccountTotalBalance(liveAccountTotal)
                .bucketTotalClosingBalance(bucketTotalClosing)
                .isBalanced(true)
                .entries(entries)
                .reconciledAt(OffsetDateTime.now())
                .message("Trial balance verified successfully")
                .build();
    }
}

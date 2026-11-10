package com.cbs.eod.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.DptProductCatalog;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.repository.DptProductCatalogRepository;
import com.cbs.depositing.dto.JournalLeg;
import com.cbs.depositing.dto.PostingRequest;
import com.cbs.depositing.dto.PostingResult;
import com.cbs.depositing.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateAccrualException;
import com.cbs.eod.dto.AccountAccrualDetail;
import com.cbs.eod.dto.AccrualBatchResult;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3b: Daily Savings and CASA Interest Accrual Service.
 * Calculates daily interest on eligible deposit accounts and generates
 * consolidated double-entry balancing journal entries at End-of-Day (EOD).
 *
 * Formula:
 * Daily Interest = round(ledger_balance * annual_rate / day_count_basis, 4)
 *
 * Accounting Equilibrium (Invariant 1):
 *   DR: 8011 (SYS_INT_EXPENSE - Interest expense on CASA deposits)
 *   CR: 4911 (SYS_INT_PAYABLE - Accrued interest payable on CASA deposits)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class InterestAccrualService {

    public static final String ACCRUAL_CHANNEL = "BATCH";
    public static final String ACCRUAL_TRX_CODE = "EOD_ACCRUAL";
    public static final String ACCRUAL_MAKER_ID = "SYSTEM_EOD";

    private final AccountRepository accountRepository;
    private final DptProductCatalogRepository dptProductCatalogRepository;
    private final PostingEngineService postingEngineService;
    private final WorkingDateService workingDateService;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final TrxJournalDoneRepository trxJournalDoneRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;

    /**
     * Executes daily savings interest accrual for the given working date.
     * If workingDate is null, the current system operational working date is used.
     *
     * @param workingDate the bank working date for accrual calculation
     * @return summary report of the accrual batch run
     * @throws DuplicateAccrualException if accrual was already performed on that date
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public AccrualBatchResult executeDailyAccrual(LocalDate workingDate) {
        LocalDate effectiveWorkingDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        String idempotencyKey = "ACCRUAL:" + effectiveWorkingDate;
        log.info("Starting EOD Daily Interest Accrual for working date: {}", effectiveWorkingDate);

        // Idempotency check: Ensure accrual is only run once per working date
        if (trxJournalActiveRepository.existsByChannelIdAndIdempotencyKey(ACCRUAL_CHANNEL, idempotencyKey)
                || trxJournalDoneRepository.existsByChannelIdAndIdempotencyKey(ACCRUAL_CHANNEL, idempotencyKey)) {
            log.warn("EOD Accrual has already been executed for working date: {}", effectiveWorkingDate);
            throw new DuplicateAccrualException(effectiveWorkingDate);
        }

        List<Account> activeAccounts = accountRepository.findByStatus("ACTIVE");
        log.info("Found {} active accounts for interest accrual evaluation", activeAccounts.size());

        Map<String, DptProductCatalog> productCache = new HashMap<>();
        List<AccountAccrualDetail> accountDetails = new ArrayList<>();
        BigDecimal totalInterestAccrued = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        int accountsAccruedCount = 0;

        for (Account account : activeAccounts) {
            DptProductCatalog product = productCache.computeIfAbsent(
                    account.getProductCode(),
                    code -> dptProductCatalogRepository.findById(code).orElse(null)
            );

            if (product == null || !"Y".equals(product.getIsActive())) {
                log.debug("Skipping account {}: product '{}' not found or inactive",
                        account.getAccountNumber(), account.getProductCode());
                continue;
            }

            BigDecimal minAccrual = (product.getMinAccrualBalance() != null && product.getMinAccrualBalance().compareTo(BigDecimal.ZERO) > 0)
                    ? product.getMinAccrualBalance()
                    : (product.getMinBalance() != null ? product.getMinBalance() : BigDecimal.ZERO);

            BigDecimal ledgerBalance = (account.getLedgerBalance() != null)
                    ? account.getLedgerBalance()
                    : BigDecimal.ZERO;

            // Ineligible if below minimum accrual balance or non-positive
            if (ledgerBalance.compareTo(minAccrual) < 0 || ledgerBalance.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal appliedRate;
            if ("CASA".equalsIgnoreCase(account.getProductType())) {
                appliedRate = (product.getAnnualRate() != null) ? product.getAnnualRate() : BigDecimal.ZERO;
            } else {
                appliedRate = (account.getEffectiveRate() != null && account.getEffectiveRate().compareTo(BigDecimal.ZERO) > 0)
                        ? account.getEffectiveRate()
                        : (product.getAnnualRate() != null ? product.getAnnualRate() : BigDecimal.ZERO);
            }

            BigDecimal dailyInterest = calculateDailyInterest(
                    ledgerBalance,
                    appliedRate,
                    product.getDayCountBasis()
            );

            if (dailyInterest.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal currentAccrued = (account.getAccruedInterest() != null)
                        ? account.getAccruedInterest()
                        : BigDecimal.ZERO;
                BigDecimal updatedAccrued = currentAccrued.add(dailyInterest);
                account.setAccruedInterest(updatedAccrued);
                accountRepository.save(account);

                totalInterestAccrued = totalInterestAccrued.add(dailyInterest);
                accountsAccruedCount++;

                accountDetails.add(AccountAccrualDetail.builder()
                        .accountId(account.getAccountId())
                        .accountNumber(account.getAccountNumber())
                        .productCode(account.getProductCode())
                        .ledgerBalance(ledgerBalance)
                        .annualRate(appliedRate)
                        .dayCountBasis(product.getDayCountBasis())
                        .dailyInterest(dailyInterest)
                        .updatedAccruedInterest(updatedAccrued)
                        .build());
            }
        }

        Long journalTrxId = null;
        String trxNumber = null;

        // Invariant 1: Post consolidated balancing journal entry if interest > 0
        if (totalInterestAccrued.compareTo(BigDecimal.ZERO) > 0) {
            PostingRequest postingRequest = PostingRequest.builder()
                    .trxCode(ACCRUAL_TRX_CODE)
                    .workingDate(effectiveWorkingDate)
                    .trxAmount(totalInterestAccrued)
                    .currency("VND")
                    .branchCode("ALL")
                    .productCode("CASA_VND")
                    .narrationOverride("Trich truoc lai tien gui CASA EOD ngay " + effectiveWorkingDate)
                    .build();

            PostingResult postingResult = postingEngineService.generateAndValidateEntries(postingRequest);

            trxNumber = "ACCRUAL-" + effectiveWorkingDate.format(DateTimeFormatter.BASIC_ISO_DATE)
                    + "-" + (System.currentTimeMillis() % 100000000);

            TrxJournalActive journal = TrxJournalActive.builder()
                    .trxNumber(trxNumber)
                    .channelId(ACCRUAL_CHANNEL)
                    .idempotencyKey(idempotencyKey)
                    .trxCode(ACCRUAL_TRX_CODE)
                    .workingDate(effectiveWorkingDate)
                    .status("COMPLETED")
                    .makerId(ACCRUAL_MAKER_ID)
                    .isReversed("N")
                    .createdAt(OffsetDateTime.now())
                    .build();

            journal = trxJournalActiveRepository.saveAndFlush(journal);
            journalTrxId = journal.getTrxId();

            for (JournalLeg leg : postingResult.legs()) {
                JournalEntryActive entry = JournalEntryActive.builder()
                        .trxId(journal.getTrxId())
                        .entryGroup(leg.entryGroup())
                        .entryIndex(leg.entryIndex())
                        .accountId(leg.accountId())
                        .glCode(leg.glCode())
                        .drCr(leg.drCr())
                        .amount(leg.amount())
                        .currency(leg.currency())
                        .valueDate(leg.valueDate())
                        .createdAt(OffsetDateTime.now())
                        .build();
                journalEntryActiveRepository.save(entry);
            }

            log.info("EOD Accrual balanced journal posted: trxId={}, trxNumber={}, totalAmount={}",
                    journalTrxId, trxNumber, totalInterestAccrued);
        } else {
            log.info("No interest accrued on working date: {} (0 eligible accounts)", effectiveWorkingDate);
        }

        return AccrualBatchResult.builder()
                .workingDate(effectiveWorkingDate)
                .totalAccountsProcessed(activeAccounts.size())
                .totalAccountsAccrued(accountsAccruedCount)
                .totalInterestAccrued(totalInterestAccrued)
                .journalTrxId(journalTrxId)
                .trxNumber(trxNumber)
                .accountDetails(accountDetails)
                .build();
    }

    /**
     * Pure calculation formula:
     * Daily Interest = round(ledger_balance * annual_rate / day_count_basis, 4)
     *
     * @param ledgerBalance current ledger balance
     * @param annualRate    annual interest rate (e.g. 0.0050 for 0.5%)
     * @param dayCountBasis day count convention ("ACT_360" or "ACT_365")
     * @return daily interest rounded to 4 decimal places with HALF_UP
     */
    public BigDecimal calculateDailyInterest(BigDecimal ledgerBalance, BigDecimal annualRate, String dayCountBasis) {
        if (ledgerBalance == null || ledgerBalance.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        if (annualRate == null || annualRate.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        }
        BigDecimal dayBasis = "ACT_360".equalsIgnoreCase(dayCountBasis)
                ? BigDecimal.valueOf(360)
                : BigDecimal.valueOf(365);

        return ledgerBalance.multiply(annualRate).divide(dayBasis, 4, RoundingMode.HALF_UP);
    }
}

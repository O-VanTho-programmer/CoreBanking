package com.cbs.eod.service;

import com.cbs.account.domain.Account;
import com.cbs.account.domain.ProductCatalog;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.repository.ProductCatalogRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.DuplicateSnapshotException;
import com.cbs.eod.domain.AccountBalanceBucket;
import com.cbs.eod.domain.AccountBalanceBucketId;
import com.cbs.eod.dto.BalanceSnapshotBatchResult;
import com.cbs.eod.repository.AccountBalanceBucketRepository;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Phase 3c: Balance Snapshot Bucket Service.
 * Freezes end-of-day balances, daily debit/credit turnovers, and daily accrued interest
 * for customer accounts into ACCOUNT_BALANCE_BUCKET.
 *
 * Enforces the core accounting turnover balance equation:
 * Closing Balance (today) = Closing Balance (yesterday) + Daily Credit Sum - Daily Debit Sum
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BalanceSnapshotService {

    private final AccountRepository accountRepository;
    private final ProductCatalogRepository productCatalogRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final JournalEntryDoneRepository journalEntryDoneRepository;
    private final AccountBalanceBucketRepository bucketRepository;
    private final WorkingDateService workingDateService;
    private final InterestAccrualService interestAccrualService;

    /**
     * Executes the end-of-day balance snapshot for the specified working date.
     * If workingDate is null, the current system operational working date is used.
     *
     * @param workingDate the working date to snapshot
     * @return summary of the snapshot batch run
     * @throws DuplicateSnapshotException if snapshot already exists for the working date
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public BalanceSnapshotBatchResult takeDailySnapshot(LocalDate workingDate) {
        LocalDate effectiveWorkingDate = (workingDate != null)
                ? workingDate
                : workingDateService.getCurrentWorkingDate();

        log.info("Starting EOD Balance Snapshot Bucket run for working date: {}", effectiveWorkingDate);

        // 1. Idempotency Check: Prevent duplicate bucket generation for the same date
        if (bucketRepository.existsByWorkingDate(effectiveWorkingDate)) {
            log.warn("Balance snapshot already exists for working date: {}", effectiveWorkingDate);
            throw new DuplicateSnapshotException(effectiveWorkingDate);
        }

        // 2. Aggregate daily debit and credit turnovers in a single query across active and done
        List<Object[]> rawTurnovers = new ArrayList<>();
        rawTurnovers.addAll(journalEntryActiveRepository.aggregateDailyTurnoverByValueDate(effectiveWorkingDate));
        rawTurnovers.addAll(journalEntryDoneRepository.aggregateDailyTurnoverByValueDate(effectiveWorkingDate));
        Map<Long, BigDecimal[]> turnoverMap = new HashMap<>();

        for (Object[] row : rawTurnovers) {
            Long acctId = (Long) row[0];
            String drCr = (String) row[1];
            BigDecimal amount = (BigDecimal) row[2];

            BigDecimal[] sums = turnoverMap.computeIfAbsent(acctId, k -> new BigDecimal[]{
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            });

            if ("D".equals(drCr)) {
                sums[0] = sums[0].add(amount);
            } else if ("C".equals(drCr)) {
                sums[1] = sums[1].add(amount);
            }
        }

        // 3. Retrieve customer accounts
        List<Account> accounts = accountRepository.findAll();
        log.info("Processing {} accounts for balance snapshot", accounts.size());

        Map<String, ProductCatalog> productCache = new HashMap<>();
        List<AccountBalanceBucket> buckets = new ArrayList<>(accounts.size());

        BigDecimal totalClosingBalance = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalDailyDebitSum = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalDailyCreditSum = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
        BigDecimal totalDailyAccruedInterest = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

        for (Account account : accounts) {
            Long accountId = account.getAccountId();
            BigDecimal closingBalance = (account.getLedgerBalance() != null)
                    ? account.getLedgerBalance().setScale(4, RoundingMode.HALF_UP)
                    : BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);

            BigDecimal[] turnovers = turnoverMap.getOrDefault(accountId, new BigDecimal[]{
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP),
                    BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP)
            });
            BigDecimal dailyDebit = turnovers[0];
            BigDecimal dailyCredit = turnovers[1];

            // Resolve daily accrued interest for this specific day
            BigDecimal dailyAccruedInterest = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            ProductCatalog product = productCache.computeIfAbsent(
                    account.getProductCode(),
                    code -> productCatalogRepository.findById(code).orElse(null)
            );

            if (product != null && "Y".equals(product.getIsActive())) {
                BigDecimal minBalance = (product.getMinBalance() != null) ? product.getMinBalance() : BigDecimal.ZERO;
                if (closingBalance.compareTo(minBalance) >= 0 && closingBalance.compareTo(BigDecimal.ZERO) > 0) {
                    dailyAccruedInterest = interestAccrualService.calculateDailyInterest(
                            closingBalance,
                            product.getAnnualRate(),
                            product.getDayCountBasis()
                    );
                }
            }

            AccountBalanceBucket bucket = AccountBalanceBucket.builder()
                    .id(new AccountBalanceBucketId(accountId, effectiveWorkingDate))
                    .closingBalance(closingBalance)
                    .dailyDebitSum(dailyDebit)
                    .dailyCreditSum(dailyCredit)
                    .accruedInterestDay(dailyAccruedInterest)
                    .createdAt(OffsetDateTime.now())
                    .build();

            buckets.add(bucket);

            totalClosingBalance = totalClosingBalance.add(closingBalance);
            totalDailyDebitSum = totalDailyDebitSum.add(dailyDebit);
            totalDailyCreditSum = totalDailyCreditSum.add(dailyCredit);
            totalDailyAccruedInterest = totalDailyAccruedInterest.add(dailyAccruedInterest);
        }

        bucketRepository.saveAll(buckets);
        log.info("Successfully persisted {} balance snapshot buckets for working date {}", buckets.size(), effectiveWorkingDate);

        return BalanceSnapshotBatchResult.builder()
                .workingDate(effectiveWorkingDate)
                .totalAccountsSnapshotted(buckets.size())
                .totalClosingBalance(totalClosingBalance)
                .totalDailyDebitSum(totalDailyDebitSum)
                .totalDailyCreditSum(totalDailyCreditSum)
                .totalDailyAccruedInterest(totalDailyAccruedInterest)
                .completedAt(OffsetDateTime.now())
                .build();
    }
}

package com.cbs.eod.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.DptProductCatalog;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.repository.DptProductCatalogRepository;
import com.cbs.deposit.service.FdRateMatrixService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class FdMaturityRolloverService {

    public static final String ROLLOVER_CHANNEL = "EOD_BATCH";
    public static final String ROLLOVER_MAKER_ID = "SYSTEM_EOD";

    private final AccountRepository accountRepository;
    private final DptProductCatalogRepository dptProductCatalogRepository;
    private final FdRateMatrixService fdRateMatrixService;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final WorkingDateService workingDateService;

    /**
     * Executes automated EOD Fixed Deposit Maturity and Rollover processing.
     *
     * @param workingDate the operational date being evaluated
     * @return number of accounts processed
     */
    @Transactional
    public int executeMaturityRollover(LocalDate workingDate) {
        LocalDate effectiveDate = (workingDate != null) ? workingDate : workingDateService.getCurrentWorkingDate();
        log.info("Starting EOD FD Maturity & Rollover evaluation for date: {}", effectiveDate);

        List<Account> maturingAccounts = accountRepository.findMaturingFdAccounts(effectiveDate);
        log.info("Found {} maturing FD accounts scheduled on or before {}", maturingAccounts.size(), effectiveDate);

        int processedCount = 0;

        for (Account account : maturingAccounts) {
            try {
                processAccount(account, effectiveDate);
                processedCount++;
            } catch (Exception e) {
                log.error("Failed to process maturity for FD account {}: {}", account.getAccountNumber(), e.getMessage(), e);
            }
        }

        log.info("EOD FD Maturity & Rollover completed. Processed {} accounts.", processedCount);
        return processedCount;
    }

    private void processAccount(Account account, LocalDate effectiveDate) {
        DptProductCatalog product = dptProductCatalogRepository.findById(account.getProductCode()).orElse(null);
        if (product == null) {
            log.warn("Skipping account {}: Product '{}' not found", account.getAccountNumber(), account.getProductCode());
            return;
        }

        String rolloverOption = (account.getRolloverOption() != null) ? account.getRolloverOption() : "NO_ROLLOVER";
        BigDecimal principal = (account.getLedgerBalance() != null) ? account.getLedgerBalance() : BigDecimal.ZERO;
        BigDecimal accruedInterest = (account.getAccruedInterest() != null) ? account.getAccruedInterest() : BigDecimal.ZERO;
        int term = (account.getTermMonths() != null && account.getTermMonths() > 0) ? account.getTermMonths() : 12;

        log.info("Processing FD maturity: account={}, option={}, principal={}, accruedInterest={}",
                account.getAccountNumber(), rolloverOption, principal, accruedInterest);

        switch (rolloverOption) {
            case "PRINCIPAL_AND_INTEREST" -> {
                // Capitalize interest into principal
                BigDecimal updatedPrincipal = principal.add(accruedInterest);
                account.setLedgerBalance(updatedPrincipal);
                account.setAccruedInterest(BigDecimal.ZERO);
                account.setMaturityDate(effectiveDate.plusMonths(term));

                applyRolloverRateRule(account, product, term, effectiveDate);

                if (accruedInterest.compareTo(BigDecimal.ZERO) > 0) {
                    postRolloverJournal(
                            account,
                            "FD_ROLLOVER_CAP",
                            product.getAccrualGlCode(), // DR: 4911 (Interest Payable)
                            account.getGlCode(),         // CR: 4211 (FD Liability)
                            accruedInterest,
                            effectiveDate,
                            "Capitalize accrued interest to principal on maturity rollover: " + account.getAccountNumber()
                    );
                }
                accountRepository.save(account);
            }

            case "PRINCIPAL_ONLY" -> {
                Account linkedCasa = getActiveLinkedCasa(account);
                if (linkedCasa != null) {
                    // Disburse interest to linked CASA
                    linkedCasa.setLedgerBalance(linkedCasa.getLedgerBalance().add(accruedInterest));
                    accountRepository.save(linkedCasa);

                    account.setAccruedInterest(BigDecimal.ZERO);
                    account.setMaturityDate(effectiveDate.plusMonths(term));

                    applyRolloverRateRule(account, product, term, effectiveDate);

                    if (accruedInterest.compareTo(BigDecimal.ZERO) > 0) {
                        postRolloverJournal(
                                account,
                                "FD_ROLLOVER_INT_PAY",
                                product.getAccrualGlCode(), // DR: 4911 (Interest Payable)
                                linkedCasa.getGlCode(),      // CR: 4211 (CASA Deposit)
                                accruedInterest,
                                effectiveDate,
                                "Disburse accrued interest to linked CASA on maturity rollover: " + account.getAccountNumber()
                        );
                    }
                    accountRepository.save(account);
                } else {
                    // No active linked CASA: transition to MATURED
                    log.warn("FD account {} has PRINCIPAL_ONLY rollover but no active linked CASA. Transitioning to MATURED.",
                            account.getAccountNumber());
                    account.setStatus("MATURED");
                    accountRepository.save(account);
                }
            }

            case "NO_ROLLOVER" -> {
                Account linkedCasa = getActiveLinkedCasa(account);
                if (linkedCasa != null) {
                    BigDecimal totalPayout = principal.add(accruedInterest);
                    linkedCasa.setLedgerBalance(linkedCasa.getLedgerBalance().add(totalPayout));
                    accountRepository.save(linkedCasa);

                    account.setLedgerBalance(BigDecimal.ZERO);
                    account.setAccruedInterest(BigDecimal.ZERO);
                    account.setStatus("CLOSED");

                    if (principal.compareTo(BigDecimal.ZERO) > 0) {
                        postRolloverJournal(
                                account,
                                "FD_CLOSE_PRIN",
                                account.getGlCode(),    // DR: 4211 (FD Liability)
                                linkedCasa.getGlCode(), // CR: 4211 (CASA Liability)
                                principal,
                                effectiveDate,
                                "Principal payout to CASA on maturity closure: " + account.getAccountNumber()
                        );
                    }

                    if (accruedInterest.compareTo(BigDecimal.ZERO) > 0) {
                        postRolloverJournal(
                                account,
                                "FD_CLOSE_INT",
                                product.getAccrualGlCode(), // DR: 4911 (Interest Payable)
                                linkedCasa.getGlCode(),      // CR: 4211 (CASA Liability)
                                accruedInterest,
                                effectiveDate,
                                "Interest payout to CASA on maturity closure: " + account.getAccountNumber()
                        );
                    }
                    accountRepository.save(account);
                } else {
                    // No active linked CASA: transition to MATURED waiting for counter withdrawal
                    log.info("FD account {} has NO_ROLLOVER without linked CASA. Transitioning to MATURED.",
                            account.getAccountNumber());
                    account.setStatus("MATURED");
                    accountRepository.save(account);
                }
            }

            default -> {
                log.warn("Unknown rolloverOption '{}' for account {}. Setting to MATURED.",
                        rolloverOption, account.getAccountNumber());
                account.setStatus("MATURED");
                accountRepository.save(account);
            }
        }
    }

    private void applyRolloverRateRule(Account account, DptProductCatalog product, int term, LocalDate effectiveDate) {
        String rule = (product.getRolloverRateRule() != null) ? product.getRolloverRateRule() : "CURRENT_MARKET_RATE";

        switch (rule) {
            case "CURRENT_MARKET_RATE" -> {
                var matrixRateOpt = fdRateMatrixService.resolveBaseRate(account.getProductCode(), term, effectiveDate);
                BigDecimal newRate = matrixRateOpt.orElse(product.getAnnualRate());
                account.setBaseInterestRate(newRate);
                account.setInterestMargin(BigDecimal.ZERO); // VIP margin ceases on auto-rollover
                account.setEffectiveRate(newRate);
                log.info("Rollover account {}: Updated rate to current market rate {}", account.getAccountNumber(), newRate);
            }
            case "ORIGINAL_CONTRACT_RATE" -> {
                log.info("Rollover account {}: Retaining original contract effective rate {}",
                        account.getAccountNumber(), account.getEffectiveRate());
            }
            case "MANUAL_CONFIRMATION" -> {
                log.info("Rollover account {}: Policy requires manual confirmation. Transitioning to MATURED.",
                        account.getAccountNumber());
                account.setStatus("MATURED");
            }
        }
    }

    private Account getActiveLinkedCasa(Account fdAccount) {
        if (fdAccount.getLinkedCasaAccountId() == null) {
            return null;
        }
        return accountRepository.findById(fdAccount.getLinkedCasaAccountId())
                .filter(a -> "ACTIVE".equalsIgnoreCase(a.getStatus()))
                .orElse(null);
    }

    private void postRolloverJournal(Account account, String trxCode, String drGl, String crGl,
                                    BigDecimal amount, LocalDate workingDate, String narration) {
        String shortCode = trxCode.length() > 8 ? trxCode.substring(0, 8) : trxCode;
        String trxNumber = "EOD-" + shortCode + "-" + (System.currentTimeMillis() % 10000000000L);
        String idempotencyKey = trxCode + ":" + account.getAccountNumber() + ":" + workingDate;

        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId(ROLLOVER_CHANNEL)
                .idempotencyKey(idempotencyKey)
                .trxCode(trxCode)
                .workingDate(workingDate)
                .status("COMPLETED")
                .makerId(ROLLOVER_MAKER_ID)
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();

        journal = trxJournalActiveRepository.saveAndFlush(journal);

        // Leg 1: Debit
        JournalEntryActive drEntry = JournalEntryActive.builder()
                .trxId(journal.getTrxId())
                .entryGroup(1)
                .entryIndex(1)
                .accountId(account.getAccountId())
                .glCode(drGl)
                .drCr("D")
                .amount(amount)
                .currency(account.getCurrency())
                .valueDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .build();
        journalEntryActiveRepository.save(drEntry);

        // Leg 2: Credit
        JournalEntryActive crEntry = JournalEntryActive.builder()
                .trxId(journal.getTrxId())
                .entryGroup(1)
                .entryIndex(2)
                .accountId(account.getAccountId())
                .glCode(crGl)
                .drCr("C")
                .amount(amount)
                .currency(account.getCurrency())
                .valueDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .build();
        journalEntryActiveRepository.save(crEntry);

        log.debug("Posted journal [trxNumber={}, amount={}, DR={}, CR={}] for narration: {}",
                trxNumber, amount, drGl, crGl, narration);
    }
}

package com.cbs.transaction.service;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.common.exception.InsufficientFundsException;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.dto.WithdrawCommand;
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
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Core Banking Transaction Orchestrator Service.
 * Implements:
 * - Invariant 1: Double-Entry Equilibrium
 * - Invariant 2: Balance Integrity (Available = Ledger - Hold)
 * - Invariant 3: Deterministic Row Locking (ORDER BY account_id ASC FOR UPDATE)
 * - Invariant 4: Working Date Decoupling
 * - Invariant 5: Two-Tier Composite Idempotency
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class TransactionService {

    private final AccountRepository accountRepository;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final PostingEngineService postingEngineService;
    private final IdempotencyService idempotencyService;
    private final WorkingDateService workingDateService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes internal transfer between two customer CASA accounts.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionReceipt transfer(TransferCommand command) {
        log.info("Executing transfer: channel='{}', key='{}', from='{}', to='{}', amount={}",
                command.channelId(), command.idempotencyKey(), command.fromAccountNumber(),
                command.toAccountNumber(), command.amount());

        // 1. Idempotency Check (Redis -> Hot -> Cold)
        Optional<TransactionReceipt> existing = idempotencyService.checkExisting(
                command.channelId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        // 2. Cutoff Guard (Invariant 4)
        checkCutoff();

        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        // 3. Resolve account IDs without loading unversioned entities into persistence context
        Long fromAccountId = accountRepository.findIdByAccountNumber(command.fromAccountNumber())
                .orElseThrow(() -> AccountNotFoundException.byNumber(command.fromAccountNumber()));
        Long toAccountId = accountRepository.findIdByAccountNumber(command.toAccountNumber())
                .orElseThrow(() -> AccountNotFoundException.byNumber(command.toAccountNumber()));

        if (fromAccountId.equals(toAccountId)) {
            throw new IllegalArgumentException("Source and destination accounts cannot be identical");
        }

        // 4. Invariant 3: Deterministic Pessimistic Row Locking (ORDER BY account_id ASC FOR UPDATE)
        List<Long> sortedIds = Stream.of(fromAccountId, toAccountId)
                .sorted()
                .toList();

        List<Account> lockedAccounts = accountRepository.findAndLockAccountsByIds(sortedIds);
        Map<Long, Account> accountMap = lockedAccounts.stream()
                .collect(Collectors.toMap(Account::getAccountId, Function.identity()));

        Account fromAccount = accountMap.get(fromAccountId);
        Account toAccount = accountMap.get(toAccountId);

        validateAccountStatus(fromAccount);
        validateAccountStatus(toAccount);

        String currency = (command.currency() != null && !command.currency().isBlank())
                ? command.currency()
                : fromAccount.getCurrency();

        BigDecimal feeAmount = (command.feeAmount() != null) ? command.feeAmount() : BigDecimal.ZERO;

        // 5. Evaluate Posting Rules via PostingEngineService
        PostingRequest postingRequest = PostingRequest.builder()
                .trxCode("TRF_INTERNAL")
                .currency(currency)
                .workingDate(workingDate)
                .trxAmount(command.amount())
                .feeAmount(feeAmount)
                .customerAccountId(fromAccount.getAccountId())
                .customerGlCode(fromAccount.getGlCode())
                .targetAccountId(toAccount.getAccountId())
                .targetGlCode(toAccount.getGlCode())
                .branchCode(fromAccount.getBranchCode())
                .productCode(fromAccount.getProductCode())
                .narrationOverride(command.narration())
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(postingRequest);

        // 6. Invariant 2 Check: Available Balance on Debited Account
        BigDecimal totalDebit = postingResult.legs().stream()
                .filter(l -> fromAccount.getAccountId().equals(l.accountId()) && "D".equals(l.drCr()))
                .map(JournalLeg::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal available = fromAccount.getAvailableBalance();
        if (available.compareTo(totalDebit) < 0) {
            log.warn("Transfer rejected: Insufficient funds on fromAccount={}: Available={}, Required={}",
                    fromAccount.getAccountId(), available, totalDebit);
            throw new InsufficientFundsException(fromAccount.getAccountId(), available, totalDebit);
        }

        // 7. Early Claim: Insert Header with status = 'IN_FLIGHT'
        String trxNumber = generateTrxNumber(workingDate);
        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("TRF_INTERNAL")
                .workingDate(workingDate)
                .status("IN_FLIGHT")
                .makerId(command.makerId())
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();

        journal = trxJournalActiveRepository.saveAndFlush(journal);

        // 8. Apply Balance Mutations based on verified legs
        for (JournalLeg leg : postingResult.legs()) {
            if (leg.accountId() != null) {
                if (leg.accountId().equals(fromAccount.getAccountId())) {
                    mutateBalance(fromAccount, leg);
                } else if (leg.accountId().equals(toAccount.getAccountId())) {
                    mutateBalance(toAccount, leg);
                }
            }
        }
        accountRepository.save(fromAccount);
        accountRepository.save(toAccount);

        // 9. Insert Immutable Journal Entry Rows
        saveJournalEntries(journal.getTrxId(), postingResult.legs());

        // 10. Update Header to COMPLETED
        journal.setStatus("COMPLETED");
        trxJournalActiveRepository.save(journal);

        // 11. Build Receipt and Cache
        TransactionReceipt receipt = TransactionReceipt.builder()
                .trxId(journal.getTrxId())
                .trxNumber(journal.getTrxNumber())
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("TRF_INTERNAL")
                .workingDate(workingDate)
                .status("COMPLETED")
                .currency(currency)
                .totalAmount(command.amount())
                .feeAmount(feeAmount)
                .fromAccountAvailableBalance(fromAccount.getAvailableBalance())
                .toAccountAvailableBalance(toAccount.getAvailableBalance())
                .legs(postingResult.legs())
                .timestamp(journal.getCreatedAt())
                .build();

        idempotencyService.recordCompletion(command.channelId(), command.idempotencyKey(), receipt);
        log.info("Transfer COMPLETED successfully: trxNumber='{}'", trxNumber);
        return receipt;
    }

    /**
     * Executes cash deposit at branch counter (CASH_DEP).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionReceipt deposit(DepositCommand command) {
        log.info("Executing cash deposit: channel='{}', key='{}', account='{}', amount={}",
                command.channelId(), command.idempotencyKey(), command.accountNumber(), command.amount());

        Optional<TransactionReceipt> existing = idempotencyService.checkExisting(
                command.channelId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        checkCutoff();
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        Long targetAccountId = accountRepository.findIdByAccountNumber(command.accountNumber())
                .orElseThrow(() -> AccountNotFoundException.byNumber(command.accountNumber()));

        // Pessimistic write lock single account
        Account account = accountRepository.findByIdForUpdate(targetAccountId)
                .orElseThrow(() -> AccountNotFoundException.byId(targetAccountId));
        validateAccountStatus(account);

        String currency = (command.currency() != null && !command.currency().isBlank())
                ? command.currency()
                : account.getCurrency();

        PostingRequest postingRequest = PostingRequest.builder()
                .trxCode("CASH_DEP")
                .currency(currency)
                .workingDate(workingDate)
                .trxAmount(command.amount())
                .customerAccountId(account.getAccountId())
                .customerGlCode(account.getGlCode())
                .branchCode(command.branchCode())
                .productCode(account.getProductCode())
                .narrationOverride(command.narration())
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(postingRequest);

        // Early Claim
        String trxNumber = generateTrxNumber(workingDate);
        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("CASH_DEP")
                .workingDate(workingDate)
                .status("IN_FLIGHT")
                .makerId(command.makerId())
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();

        journal = trxJournalActiveRepository.saveAndFlush(journal);

        // Mutate customer balance
        for (JournalLeg leg : postingResult.legs()) {
            if (account.getAccountId().equals(leg.accountId())) {
                mutateBalance(account, leg);
            }
        }
        accountRepository.save(account);

        saveJournalEntries(journal.getTrxId(), postingResult.legs());

        journal.setStatus("COMPLETED");
        trxJournalActiveRepository.save(journal);

        TransactionReceipt receipt = TransactionReceipt.builder()
                .trxId(journal.getTrxId())
                .trxNumber(journal.getTrxNumber())
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("CASH_DEP")
                .workingDate(workingDate)
                .status("COMPLETED")
                .currency(currency)
                .totalAmount(command.amount())
                .feeAmount(BigDecimal.ZERO)
                .toAccountAvailableBalance(account.getAvailableBalance())
                .legs(postingResult.legs())
                .timestamp(journal.getCreatedAt())
                .build();

        idempotencyService.recordCompletion(command.channelId(), command.idempotencyKey(), receipt);
        return receipt;
    }

    /**
     * Executes cash withdrawal at counter / ATM (CASH_WDR).
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TransactionReceipt withdraw(WithdrawCommand command) {
        log.info("Executing cash withdrawal: channel='{}', key='{}', account='{}', amount={}",
                command.channelId(), command.idempotencyKey(), command.accountNumber(), command.amount());

        Optional<TransactionReceipt> existing = idempotencyService.checkExisting(
                command.channelId(), command.idempotencyKey());
        if (existing.isPresent()) {
            return existing.get();
        }

        checkCutoff();
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        Long targetAccountId = accountRepository.findIdByAccountNumber(command.accountNumber())
                .orElseThrow(() -> AccountNotFoundException.byNumber(command.accountNumber()));

        Account account = accountRepository.findByIdForUpdate(targetAccountId)
                .orElseThrow(() -> AccountNotFoundException.byId(targetAccountId));
        validateAccountStatus(account);

        String currency = (command.currency() != null && !command.currency().isBlank())
                ? command.currency()
                : account.getCurrency();

        PostingRequest postingRequest = PostingRequest.builder()
                .trxCode("CASH_WDR")
                .currency(currency)
                .workingDate(workingDate)
                .trxAmount(command.amount())
                .customerAccountId(account.getAccountId())
                .customerGlCode(account.getGlCode())
                .branchCode(command.branchCode())
                .productCode(account.getProductCode())
                .narrationOverride(command.narration())
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(postingRequest);

        // Invariant 2 Check: Available >= Amount
        if (account.getAvailableBalance().compareTo(command.amount()) < 0) {
            throw new InsufficientFundsException(account.getAccountId(), account.getAvailableBalance(), command.amount());
        }

        String trxNumber = generateTrxNumber(workingDate);
        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("CASH_WDR")
                .workingDate(workingDate)
                .status("IN_FLIGHT")
                .makerId(command.makerId())
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();

        journal = trxJournalActiveRepository.saveAndFlush(journal);

        for (JournalLeg leg : postingResult.legs()) {
            if (account.getAccountId().equals(leg.accountId())) {
                mutateBalance(account, leg);
            }
        }
        accountRepository.save(account);

        saveJournalEntries(journal.getTrxId(), postingResult.legs());

        journal.setStatus("COMPLETED");
        trxJournalActiveRepository.save(journal);

        TransactionReceipt receipt = TransactionReceipt.builder()
                .trxId(journal.getTrxId())
                .trxNumber(journal.getTrxNumber())
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("CASH_WDR")
                .workingDate(workingDate)
                .status("COMPLETED")
                .currency(currency)
                .totalAmount(command.amount())
                .feeAmount(BigDecimal.ZERO)
                .fromAccountAvailableBalance(account.getAvailableBalance())
                .legs(postingResult.legs())
                .timestamp(journal.getCreatedAt())
                .build();

        idempotencyService.recordCompletion(command.channelId(), command.idempotencyKey(), receipt);
        return receipt;
    }

    private void mutateBalance(Account account, JournalLeg leg) {
        if ("D".equals(leg.drCr())) {
            account.setLedgerBalance(account.getLedgerBalance().subtract(leg.amount())
                    .setScale(4, RoundingMode.HALF_UP));
        } else if ("C".equals(leg.drCr())) {
            account.setLedgerBalance(account.getLedgerBalance().add(leg.amount())
                    .setScale(4, RoundingMode.HALF_UP));
        }
    }

    private void saveJournalEntries(Long trxId, List<JournalLeg> legs) {
        for (JournalLeg leg : legs) {
            JournalEntryActive entry = JournalEntryActive.builder()
                    .trxId(trxId)
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
    }

    private String generateTrxNumber(LocalDate workingDate) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        String dateStr = workingDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("TRX-%s-%08d", dateStr, seq);
    }

    private void checkCutoff() {
        if (workingDateService.isSystemCutoff()) {
            throw new IllegalStateException("Core banking system is currently in Cutoff for EOD batch processing");
        }
    }

    private void validateAccountStatus(Account account) {
        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException(String.format(
                    "Account '%s' is not in ACTIVE status (current status: '%s')",
                    account.getAccountNumber(), account.getStatus()));
        }
    }
}

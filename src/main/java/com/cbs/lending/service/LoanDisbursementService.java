package com.cbs.lending.service;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.AccountNotFoundException;
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
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * Phase 4b: Loan Disbursement Service.
 * Orchestrates loan disbursement into the borrower's linked CASA account.
 *
 * Enforces:
 * - Invariant 1: Double-Entry Equilibrium (DR 2111 / CR 4211 via PostingEngineService)
 * - Invariant 2: Balance Integrity (CASA available_balance = ledger_balance - hold_balance)
 * - Invariant 3: Deterministic Pessimistic Row Locking (LoanAccount -> CASA Account FOR UPDATE)
 * - Invariant 4: Working Date Decoupling & EOD Cutoff Guard
 * - Invariant 5: Two-Tier Composite Idempotency (channelId + idempotencyKey)
 * - Invariant 6: Status Validation via Dynamic CodeList (LoanStatus.APPROVED -> ACTIVE)
 * - Solution 1: Subsidiary Ledger Lineage (persists loan_id on asset debit leg in JOURNAL_ENTRY)
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanDisbursementService {

    private final LoanAccountRepository loanAccountRepository;
    private final AccountRepository accountRepository;
    private final PostingEngineService postingEngineService;
    private final IdempotencyService idempotencyService;
    private final WorkingDateService workingDateService;
    private final TrxJournalActiveRepository trxJournalActiveRepository;
    private final JournalEntryActiveRepository journalEntryActiveRepository;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Executes loan disbursement into the borrower's linked CASA account.
     */
    @Transactional(isolation = Isolation.READ_COMMITTED)
    public LoanDisbursementReceipt disburse(DisburseLoanCommand command) {
        log.info("Executing loan disbursement: loanId={}, channel='{}', key='{}'",
                command.loanId(), command.channelId(), command.idempotencyKey());

        // 1. Idempotency Check (Redis -> Cold/Hot Journal)
        Optional<TransactionReceipt> existing = idempotencyService.checkExisting(
                command.channelId(), command.idempotencyKey());
        if (existing.isPresent()) {
            TransactionReceipt r = existing.get();
            LoanAccount loan = loanAccountRepository.findById(command.loanId())
                    .orElseThrow(() -> new LoanAccountNotFoundException(command.loanId()));
            Account casa = accountRepository.findById(loan.getLinkedCasaAccountId())
                    .orElseThrow(() -> AccountNotFoundException.byId(loan.getLinkedCasaAccountId()));
            log.info("Idempotency HIT for loan disbursement [channel={}, key={}]: replaying receipt",
                    command.channelId(), command.idempotencyKey());
            return LoanDisbursementReceipt.builder()
                    .trxId(r.trxId())
                    .trxNumber(r.trxNumber())
                    .loanId(loan.getLoanId())
                    .loanAccountNumber(loan.getLoanAccountNumber())
                    .casaAccountId(casa.getAccountId())
                    .casaAccountNumber(casa.getAccountNumber())
                    .disbursedAmount(r.totalAmount())
                    .casaAvailableBalance(r.toAccountAvailableBalance())
                    .currency(r.currency())
                    .workingDate(r.workingDate())
                    .disbursedAt(loan.getDisbursedAt())
                    .status(r.status())
                    .legs(r.legs())
                    .build();
        }

        // 2. Cutoff Guard (Invariant 4)
        if (workingDateService.isSystemCutoff()) {
            throw new IllegalStateException("System is currently in CUTOFF state. Loan disbursement is temporarily blocked.");
        }
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        // 3. Invariant 3: Deterministic Row Locking
        // Step 3a: Pessimistic write lock on LoanAccount
        LoanAccount loan = loanAccountRepository.findByIdForUpdate(command.loanId())
                .orElseThrow(() -> new LoanAccountNotFoundException(command.loanId()));

        if (LoanStatus.ACTIVE.name().equals(loan.getLoanStatus())) {
            throw new LoanAlreadyDisbursedException(loan.getLoanId(), loan.getLoanStatus());
        }
        if (!LoanStatus.APPROVED.name().equals(loan.getLoanStatus())) {
            throw new LoanNotApprovedException(loan.getLoanId(), loan.getLoanStatus());
        }

        // Step 3b: Pessimistic write lock on linked CASA Account
        Account casaAccount = accountRepository.findByIdForUpdate(loan.getLinkedCasaAccountId())
                .orElseThrow(() -> AccountNotFoundException.byId(loan.getLinkedCasaAccountId()));

        if (!casaAccount.getCustomerId().equals(loan.getCustomerId())) {
            throw new InvalidLoanParameterException(String.format(
                    "Linked CASA account %d does not belong to borrower customer %d",
                    casaAccount.getAccountId(), loan.getCustomerId()));
        }

        if (!"ACTIVE".equals(casaAccount.getStatus())) {
            throw new InvalidLoanParameterException(String.format(
                    "Linked CASA account %d is not ACTIVE. Current status: '%s'",
                    casaAccount.getAccountId(), casaAccount.getStatus()));
        }

        // 4. Invariant 1: Evaluate Double-Entry Posting Rules for LOAN_DISBURSE
        String currency = casaAccount.getCurrency() != null ? casaAccount.getCurrency() : "VND";
        String branchCode = (command.branchCode() != null && !command.branchCode().isBlank())
                ? command.branchCode()
                : casaAccount.getBranchCode();

        PostingRequest postingRequest = PostingRequest.builder()
                .trxCode("LOAN_DISBURSE")
                .currency(currency)
                .workingDate(workingDate)
                .trxAmount(loan.getPrincipalAmount())
                .customerAccountId(casaAccount.getAccountId())
                .customerGlCode(casaAccount.getGlCode())
                .loanId(loan.getLoanId())
                .branchCode(branchCode)
                .productCode(loan.getProductCode())
                .narrationOverride(command.narrationOverride())
                .build();

        PostingResult postingResult = postingEngineService.generateAndValidateEntries(postingRequest);

        // 5. Early Claim Header
        String trxNumber = generateTrxNumber(workingDate);
        TrxJournalActive journal = TrxJournalActive.builder()
                .trxNumber(trxNumber)
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("LOAN_DISBURSE")
                .workingDate(workingDate)
                .status("IN_FLIGHT")
                .makerId(command.makerId() != null ? command.makerId() : "SYSTEM")
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();
        journal = trxJournalActiveRepository.saveAndFlush(journal);

        // 6. Invariant 2: Mutate CASA Account Balance (Credit leg increases balance)
        casaAccount.setLedgerBalance(casaAccount.getLedgerBalance().add(loan.getPrincipalAmount()));
        accountRepository.save(casaAccount);

        // 7. Transition Loan Account to ACTIVE
        OffsetDateTime now = OffsetDateTime.now();
        loan.setLoanStatus(LoanStatus.ACTIVE.name());
        loan.setDisbursedAt(now);
        loanAccountRepository.save(loan);

        // 8. Persist Balanced Journal Entries with Loan Reference (Solution 1)
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

        // 9. Finalize Journal Header to COMPLETED
        journal.setStatus("COMPLETED");
        trxJournalActiveRepository.save(journal);

        // 10. Record Completion in Idempotency Store
        TransactionReceipt trxReceipt = TransactionReceipt.builder()
                .trxId(journal.getTrxId())
                .trxNumber(journal.getTrxNumber())
                .channelId(command.channelId())
                .idempotencyKey(command.idempotencyKey())
                .trxCode("LOAN_DISBURSE")
                .workingDate(workingDate)
                .status("COMPLETED")
                .currency(currency)
                .totalAmount(loan.getPrincipalAmount())
                .toAccountAvailableBalance(casaAccount.getAvailableBalance())
                .legs(postingResult.legs())
                .timestamp(now)
                .build();
        idempotencyService.recordCompletion(command.channelId(), command.idempotencyKey(), trxReceipt);

        log.info("Loan disbursement COMPLETED: loanId={}, trxNumber='{}', amount={}, casaAccount='{}'",
                loan.getLoanId(), trxNumber, loan.getPrincipalAmount(), casaAccount.getAccountNumber());

        return LoanDisbursementReceipt.builder()
                .trxId(journal.getTrxId())
                .trxNumber(journal.getTrxNumber())
                .loanId(loan.getLoanId())
                .loanAccountNumber(loan.getLoanAccountNumber())
                .casaAccountId(casaAccount.getAccountId())
                .casaAccountNumber(casaAccount.getAccountNumber())
                .disbursedAmount(loan.getPrincipalAmount())
                .casaAvailableBalance(casaAccount.getAvailableBalance())
                .currency(currency)
                .workingDate(workingDate)
                .disbursedAt(now)
                .status("COMPLETED")
                .legs(postingResult.legs())
                .build();
    }

    private String generateTrxNumber(LocalDate workingDate) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        String dateStr = workingDate.format(DateTimeFormatter.BASIC_ISO_DATE);
        return String.format("TRX-%s-%08d", dateStr, seq);
    }
}

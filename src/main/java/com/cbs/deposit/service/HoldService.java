package com.cbs.deposit.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.domain.AccountHold;
import com.cbs.deposit.dto.PlaceHoldCommand;
import com.cbs.deposit.repository.AccountHoldRepository;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.common.exception.InsufficientFundsException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Funds Earmark / Hold Management Service.
 * Implements Invariant 2: Available = Ledger - Hold.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class HoldService {

    private final AccountRepository accountRepository;
    private final AccountHoldRepository holdRepository;
    private final WorkingDateService workingDateService;
    private final CodeListService codeListService;

    /**
     * Places a hold on account funds with strict Invariant 2 check.
     */
    @Transactional
    public AccountHold placeHold(PlaceHoldCommand command) {
        log.info("Placing hold on accountId={} for amount={}, reason='{}'",
                command.accountId(), command.amount(), command.reasonCode());

        if (!codeListService.isValid("HOLD_REASON", command.reasonCode())) {
            throw new IllegalArgumentException("Invalid HOLD_REASON: " + command.reasonCode());
        }

        // Lock account with pessimistic write lock
        Account account = accountRepository.findByIdForUpdate(command.accountId())
                .orElseThrow(() -> AccountNotFoundException.byId(command.accountId()));

        if (!"ACTIVE".equals(account.getStatus())) {
            throw new IllegalStateException("Cannot place hold on non-ACTIVE account (status=" + account.getStatus() + ")");
        }

        BigDecimal requestedAmount = command.amount().setScale(4, RoundingMode.HALF_UP);
        BigDecimal available = account.getAvailableBalance();

        // Invariant 2 Check: Available >= holdAmount
        if (available.compareTo(requestedAmount) < 0) {
            log.warn("Hold rejected on accountId={}: Available={}, Requested={}",
                    account.getAccountId(), available, requestedAmount);
            throw new InsufficientFundsException(account.getAccountId(), available, requestedAmount);
        }

        // Mutate account hold_balance
        account.setHoldBalance(account.getHoldBalance().add(requestedAmount).setScale(4, RoundingMode.HALF_UP));
        accountRepository.save(account);

        LocalDate workingDate = workingDateService.getCurrentWorkingDate();

        AccountHold hold = AccountHold.builder()
                .accountId(account.getAccountId())
                .holdAmount(requestedAmount)
                .reasonCode(command.reasonCode())
                .referenceId(command.referenceId())
                .status("ACTIVE")
                .createdWorkingDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .releasedAt(null) // Constraint: ACTIVE <=> released_at IS NULL
                .build();

        AccountHold saved = holdRepository.save(hold);
        log.info("Hold placed successfully with holdId={}, new holdBalance={}, available={}",
                saved.getHoldId(), account.getHoldBalance(), account.getAvailableBalance());
        return saved;
    }

    /**
     * Releases an active hold and restores available balance.
     */
    @Transactional
    public AccountHold releaseHold(Long holdId) {
        log.info("Releasing holdId={}", holdId);

        AccountHold hold = holdRepository.findById(holdId)
                .orElseThrow(() -> new IllegalArgumentException("Hold not found with id: " + holdId));

        if (!"ACTIVE".equals(hold.getStatus())) {
            throw new IllegalStateException("Hold is already inactive (status=" + hold.getStatus() + ")");
        }

        // Lock account
        Account account = accountRepository.findByIdForUpdate(hold.getAccountId())
                .orElseThrow(() -> AccountNotFoundException.byId(hold.getAccountId()));

        BigDecimal newHold = account.getHoldBalance().subtract(hold.getHoldAmount());
        if (newHold.compareTo(BigDecimal.ZERO) < 0) {
            newHold = BigDecimal.ZERO.setScale(4);
        }
        account.setHoldBalance(newHold.setScale(4, RoundingMode.HALF_UP));
        accountRepository.save(account);

        // Update hold status and released_at
        hold.setStatus("RELEASED");
        hold.setReleasedAt(OffsetDateTime.now()); // Constraint: RELEASED <=> released_at IS NOT NULL
        AccountHold updated = holdRepository.save(hold);

        log.info("Hold released successfully with holdId={}, new holdBalance={}, available={}",
                updated.getHoldId(), account.getHoldBalance(), account.getAvailableBalance());
        return updated;
    }

    @Transactional(readOnly = true)
    public List<AccountHold> getActiveHolds(Long accountId) {
        return holdRepository.findByAccountIdAndStatus(accountId, "ACTIVE");
    }
}

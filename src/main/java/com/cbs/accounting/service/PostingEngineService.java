package com.cbs.accounting.service;

import com.cbs.accounting.domain.AccountingRuleDefinition;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.repository.AccountingRuleRepository;
import com.cbs.common.exception.AccountingRuleNotFoundException;
import com.cbs.common.exception.UnbalancedTransactionException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Core Financial Posting Engine.
 * Evaluates the Governed Posting Rule Matrix, resolves abstract account mappings,
 * and enforces Invariant 1 (Double-Entry Equilibrium) prior to ledger persistence.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class PostingEngineService {

    private final AccountingRuleRepository ruleRepository;
    private final AccountResolverService accountResolverService;

    /**
     * Evaluates a PostingRequest against the configured accounting rule matrix,
     * resolves all accounts, and strictly verifies double-entry equilibrium.
     *
     * @param request the input transaction parameters
     * @return verified, balanced PostingResult containing immutable JournalLegs
     * @throws AccountingRuleNotFoundException if no rules match trxCode on workingDate
     * @throws UnbalancedTransactionException  if SUM(Debit) != SUM(Credit)
     */
    public PostingResult generateAndValidateEntries(PostingRequest request) {
        log.info("Evaluating posting rules for trxCode='{}' on workingDate='{}' (amount={}, ccy={})",
                request.trxCode(), request.workingDate(), request.trxAmount(), request.currency());

        List<AccountingRuleDefinition> rules = ruleRepository.findActiveRulesByTrxCode(
                request.trxCode(), request.workingDate());

        if (rules.isEmpty()) {
            throw new AccountingRuleNotFoundException(request.trxCode(), request.workingDate());
        }

        List<JournalLeg> legs = new ArrayList<>();

        for (AccountingRuleDefinition rule : rules) {
            // 1. Check fee requirement: skip optional fee leg if fee is null or zero
            if ("Y".equals(rule.getRequiresFee())) {
                if (request.feeAmount() == null || request.feeAmount().compareTo(BigDecimal.ZERO) <= 0) {
                    log.debug("Skipping zero-fee rule leg (ruleId={}, trxCode={})",
                            rule.getRuleId(), request.trxCode());
                    continue;
                }
            }

            // 2. Resolve amount by amount_source
            BigDecimal rawAmount = resolveAmount(rule.getAmountSource(), request);
            if (rawAmount == null || rawAmount.compareTo(BigDecimal.ZERO) <= 0) {
                throw new IllegalArgumentException(String.format(
                        "Amount for source '%s' in rule %d must be strictly positive, received: %s",
                        rule.getAmountSource(), rule.getRuleId(), rawAmount));
            }
            BigDecimal scaledAmount = rawAmount.setScale(4, RoundingMode.HALF_UP);

            // 3. Resolve account ID and physical GL code by account_role
            Long accountId = null;
            String physicalGlCode;

            switch (rule.getAccountRole()) {
                case "SYSTEM_GL" -> {
                    physicalGlCode = accountResolverService.resolvePhysicalGl(
                            rule.getAbstractAcctName(),
                            request.branchCode(),
                            request.currency(),
                            request.productCode());
                }
                case "CUSTOMER_ACCOUNT" -> {
                    if (request.customerAccountId() == null) {
                        throw new IllegalArgumentException(
                                "customerAccountId is mandatory for role 'CUSTOMER_ACCOUNT'");
                    }
                    if (request.customerGlCode() == null || request.customerGlCode().isBlank()) {
                        throw new IllegalArgumentException(
                                "customerGlCode is mandatory for role 'CUSTOMER_ACCOUNT'");
                    }
                    accountId = request.customerAccountId();
                    physicalGlCode = request.customerGlCode();
                }
                case "TARGET_ACCOUNT" -> {
                    if (request.targetAccountId() == null) {
                        throw new IllegalArgumentException(
                                "targetAccountId is mandatory for role 'TARGET_ACCOUNT'");
                    }
                    if (request.targetGlCode() == null || request.targetGlCode().isBlank()) {
                        throw new IllegalArgumentException(
                                "targetGlCode is mandatory for role 'TARGET_ACCOUNT'");
                    }
                    accountId = request.targetAccountId();
                    physicalGlCode = request.targetGlCode();
                }
                default -> throw new IllegalArgumentException(
                        "Unsupported accountRole: " + rule.getAccountRole());
            }

            // 4. Resolve narration
            String narration = (request.narrationOverride() != null && !request.narrationOverride().isBlank())
                    ? request.narrationOverride()
                    : rule.getNarrationTemplate();

            legs.add(new JournalLeg(
                    rule.getEntryGroup(),
                    rule.getEntryIndex(),
                    accountId,
                    physicalGlCode,
                    rule.getDebitOrCredit(),
                    scaledAmount,
                    request.currency(),
                    request.workingDate(),
                    narration
            ));
        }

        // 5. Enforce Invariant 1: Double-Entry Equilibrium Checks
        enforceDoubleEntryEquilibrium(request.trxCode(), legs);

        BigDecimal totalDebit = sumByDrCr(legs, "D");
        BigDecimal totalCredit = sumByDrCr(legs, "C");

        log.info("Posting verification SUCCESS for trxCode='{}'. Total Debit = {}, Total Credit = {}, Legs = {}",
                request.trxCode(), totalDebit, totalCredit, legs.size());

        return new PostingResult(
                request.trxCode(),
                request.workingDate(),
                request.currency(),
                totalDebit,
                totalCredit,
                legs
        );
    }

    private BigDecimal resolveAmount(String amountSource, PostingRequest request) {
        return switch (amountSource) {
            case "TRX_AMOUNT" -> request.trxAmount();
            case "FEE_AMOUNT" -> request.feeAmount();
            case "TAX_AMOUNT" -> request.taxAmount();
            case "PENALTY_AMOUNT" -> request.penaltyAmount();
            default -> throw new IllegalArgumentException("Unknown amountSource: " + amountSource);
        };
    }

    private void enforceDoubleEntryEquilibrium(String trxCode, List<JournalLeg> legs) {
        if (legs.isEmpty()) {
            throw new IllegalArgumentException("Posting leg list cannot be empty");
        }

        // Group-Level Equilibrium Verification
        Map<Integer, List<JournalLeg>> byGroup = legs.stream()
                .collect(Collectors.groupingBy(JournalLeg::entryGroup));

        for (Map.Entry<Integer, List<JournalLeg>> entry : byGroup.entrySet()) {
            int group = entry.getKey();
            List<JournalLeg> groupLegs = entry.getValue();

            BigDecimal groupDebit = sumByDrCr(groupLegs, "D");
            BigDecimal groupCredit = sumByDrCr(groupLegs, "C");

            if (groupDebit.compareTo(groupCredit) != 0) {
                log.error("Group {} is unbalanced for trxCode='{}': Debit={}, Credit={}",
                        group, trxCode, groupDebit, groupCredit);
                throw new UnbalancedTransactionException(trxCode, group, groupDebit, groupCredit);
            }
        }

        // Aggregate Transaction Equilibrium Verification
        BigDecimal totalDebit = sumByDrCr(legs, "D");
        BigDecimal totalCredit = sumByDrCr(legs, "C");

        if (totalDebit.compareTo(totalCredit) != 0) {
            log.error("Aggregate posting is unbalanced for trxCode='{}': TotalDebit={}, TotalCredit={}",
                    trxCode, totalDebit, totalCredit);
            throw new UnbalancedTransactionException(trxCode, null, totalDebit, totalCredit);
        }
    }

    private BigDecimal sumByDrCr(List<JournalLeg> legs, String drCr) {
        return legs.stream()
                .filter(l -> drCr.equals(l.drCr()))
                .map(JournalLeg::amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }
}

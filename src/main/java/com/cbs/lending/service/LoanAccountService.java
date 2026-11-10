package com.cbs.lending.service;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.InvalidLoanParameterException;
import com.cbs.common.exception.LoanAccountNotFoundException;
import com.cbs.common.exception.LoanProductNotFoundException;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.lending.domain.CreditProductCatalog;
import com.cbs.lending.domain.CreditProductIfcMapping;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanAccountIfc;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.domain.RepaymentType;
import com.cbs.lending.dto.AmortizationResult;
import com.cbs.lending.dto.AmortizationScheduleItem;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.repository.CreditProductCatalogRepository;
import com.cbs.lending.repository.CreditProductIfcMappingRepository;
import com.cbs.lending.repository.LoanAccountIfcRepository;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 4a: Loan Account & Contract Management Service.
 * Coordinates customer loan creation, policy validation against CreditProductCatalog,
 * and initial amortization schedule generation.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LoanAccountService {

    private final LoanAccountRepository loanAccountRepository;
    private final LoanScheduleRepository loanScheduleRepository;
    private final CreditProductCatalogRepository creditProductCatalogRepository;
    private final CreditProductIfcMappingRepository creditProductIfcMappingRepository;
    private final LoanAccountIfcRepository loanAccountIfcRepository;
    private final CustomerRepository customerRepository;
    private final AccountRepository accountRepository;
    private final AmortizationScheduleService amortizationScheduleService;
    private final CodeListService codeListService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Creates a new loan contract and generates its repayment schedule.
     */
    @Transactional
    public LoanAccount createLoanContract(CreateLoanContractCommand command) {
        log.info("Creating loan contract for customerId={}, product='{}', amount={}, term={} months",
                command.customerId(), command.productCode(), command.principalAmount(), command.termMonths());

        // 1. Verify customer exists
        if (!customerRepository.existsById(command.customerId())) {
            throw CustomerNotFoundException.byId(command.customerId());
        }

        // 2. Verify linked CASA account exists, belongs to customer, and is active
        Account linkedCasa = accountRepository.findById(command.linkedCasaAccountId())
                .orElseThrow(() -> AccountNotFoundException.byId(command.linkedCasaAccountId()));

        if (!linkedCasa.getCustomerId().equals(command.customerId())) {
            throw new InvalidLoanParameterException("Linked CASA account " + command.linkedCasaAccountId()
                    + " does not belong to customer " + command.customerId());
        }

        if (!"ACTIVE".equals(linkedCasa.getStatus())) {
            throw new InvalidLoanParameterException("Linked CASA account is not ACTIVE: status=" + linkedCasa.getStatus());
        }

        // 3. Verify credit product exists and is active
        CreditProductCatalog product = creditProductCatalogRepository.findByProductCodeAndIsActive(command.productCode(), "Y")
                .orElseThrow(() -> new LoanProductNotFoundException(command.productCode()));

        // 4. Validate principal amount limits
        if (command.principalAmount() == null || command.principalAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new InvalidLoanParameterException("Principal amount must be positive");
        }
        if (command.principalAmount().compareTo(product.getMinPrincipalAmount()) < 0) {
            throw new InvalidLoanParameterException("Principal amount " + command.principalAmount()
                    + " is below product minimum " + product.getMinPrincipalAmount());
        }
        if (command.principalAmount().compareTo(product.getMaxPrincipalAmount()) > 0) {
            throw new InvalidLoanParameterException("Principal amount " + command.principalAmount()
                    + " exceeds product maximum " + product.getMaxPrincipalAmount());
        }

        // 5. Validate term months limits
        if (command.termMonths() == null || command.termMonths() <= 0) {
            throw new InvalidLoanParameterException("Term months must be positive");
        }
        if (command.termMonths() < product.getMinTermMonths()) {
            throw new InvalidLoanParameterException("Term months " + command.termMonths()
                    + " is below product minimum " + product.getMinTermMonths());
        }
        if (command.termMonths() > product.getMaxTermMonths()) {
            throw new InvalidLoanParameterException("Term months " + command.termMonths()
                    + " exceeds product maximum " + product.getMaxTermMonths());
        }

        // 6. Resolve margin and effective interest rate
        BigDecimal margin = (command.interestMargin() != null)
                ? command.interestMargin()
                : product.getDefaultMarginRate();

        if (margin.compareTo(product.getMinMarginRate()) < 0 || margin.compareTo(product.getMaxMarginRate()) > 0) {
            throw new InvalidLoanParameterException(String.format(
                    "Interest margin %s is outside allowed bounds [%s, %s] for product '%s'",
                    margin, product.getMinMarginRate(), product.getMaxMarginRate(), product.getProductCode()));
        }

        BigDecimal annualRate = (command.interestRateOverride() != null)
                ? command.interestRateOverride()
                : product.getBaseInterestRate().add(margin).setScale(4, RoundingMode.HALF_UP);

        BigDecimal overdueRate = annualRate.multiply(product.getOverdueRateMultiplier())
                .setScale(4, RoundingMode.HALF_UP);

        String repaymentType = (command.repaymentTypeOverride() != null)
                ? command.repaymentTypeOverride().toUpperCase()
                : product.getDefaultRepaymentType();

        if (!codeListService.isValid("REPAYMENT_TYPE", repaymentType)) {
            throw new InvalidLoanParameterException("Invalid repayment type: " + repaymentType);
        }

        if (!codeListService.isValid("LOAN_STATUS", LoanStatus.APPROVED.name())) {
            throw new InvalidLoanParameterException("Invalid initial loan status: " + LoanStatus.APPROVED.name());
        }

        LocalDate firstDueDate = (command.firstDueDate() != null)
                ? command.firstDueDate()
                : LocalDate.now().plusMonths(1);

        String branchCode = (command.branchCode() != null && !command.branchCode().isBlank())
                ? command.branchCode()
                : linkedCasa.getBranchCode();

        String loanAccountNumber = generateLoanAccountNumber(branchCode);

        // 7. Persist Loan Account
        LoanAccount loanAccount = LoanAccount.builder()
                .loanAccountNumber(loanAccountNumber)
                .customerId(command.customerId())
                .linkedCasaAccountId(command.linkedCasaAccountId())
                .productCode(product.getProductCode())
                .principalAmount(command.principalAmount().setScale(4, RoundingMode.HALF_UP))
                .remainingPrincipal(command.principalAmount().setScale(4, RoundingMode.HALF_UP))
                .annualInterestRate(annualRate.setScale(4, RoundingMode.HALF_UP))
                .interestMargin(margin.setScale(4, RoundingMode.HALF_UP))
                .overdueInterestRate(overdueRate)
                .termMonths(command.termMonths())
                .repaymentType(repaymentType)
                .dayCountBasis(product.getDayCountBasis())
                .loanStatus(LoanStatus.APPROVED.name())
                .createdAt(OffsetDateTime.now())
                .build();

        loanAccount = loanAccountRepository.save(loanAccount);

        // 8. Clone bundled IFC charges from Product Catalog to Loan Account IFC
        List<CreditProductIfcMapping> mappings = creditProductIfcMappingRepository.findByProductCode(product.getProductCode());
        for (CreditProductIfcMapping mapping : mappings) {
            BigDecimal applied = mapping.getDefaultRate();
            BigDecimal mRate = BigDecimal.ZERO;
            if ("NORMAL_INT".equals(mapping.getIfcCode())) {
                applied = annualRate;
                mRate = margin;
            } else if ("OVERDUE_INT".equals(mapping.getIfcCode())) {
                applied = overdueRate;
            }
            LoanAccountIfc loanIfc = LoanAccountIfc.builder()
                    .loanId(loanAccount.getLoanId())
                    .ifcCode(mapping.getIfcCode())
                    .appliedRate(applied)
                    .marginRate(mRate)
                    .isWaived("N")
                    .createdAt(OffsetDateTime.now())
                    .build();
            loanAccountIfcRepository.save(loanIfc);
        }

        // 9. Generate and persist Amortization Schedule
        AmortizationResult result = amortizationScheduleService.calculateSchedule(
                loanAccount.getPrincipalAmount(),
                loanAccount.getAnnualInterestRate(),
                loanAccount.getTermMonths(),
                loanAccount.getRepaymentType(),
                firstDueDate
        );

        List<LoanSchedule> schedules = new ArrayList<>(result.items().size());
        for (AmortizationScheduleItem item : result.items()) {
            schedules.add(LoanSchedule.builder()
                    .loanId(loanAccount.getLoanId())
                    .periodNumber(item.periodNumber())
                    .dueDate(item.dueDate())
                    .principalDue(item.principalDue())
                    .interestDue(item.interestDue())
                    .totalInstallment(item.totalInstallment())
                    .principalPaid(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .interestPaid(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .penaltyDue(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .penaltyPaid(BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP))
                    .status(LoanScheduleStatus.PENDING.name())
                    .createdAt(OffsetDateTime.now())
                    .build());
        }
        loanScheduleRepository.saveAll(schedules);

        // Update maturity date on loan account
        LocalDate maturityDate = result.items().get(result.items().size() - 1).dueDate();
        loanAccount.setMaturityDate(maturityDate);
        loanAccount = loanAccountRepository.save(loanAccount);

        log.info("Loan contract created successfully: loanId={}, accountNumber='{}', maturityDate={}, ifcsBound={}",
                loanAccount.getLoanId(), loanAccount.getLoanAccountNumber(), maturityDate, mappings.size());

        return loanAccount;
    }

    @Transactional(readOnly = true)
    public LoanAccount getLoanAccount(Long loanId) {
        return loanAccountRepository.findById(loanId)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanId));
    }

    @Transactional(readOnly = true)
    public LoanAccount getLoanAccountByNumber(String accountNumber) {
        return loanAccountRepository.findByLoanAccountNumber(accountNumber)
                .orElseThrow(() -> new LoanAccountNotFoundException(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<LoanSchedule> getLoanSchedule(Long loanId) {
        return loanScheduleRepository.findByLoanIdOrderByPeriodNumberAsc(loanId);
    }

    private String generateLoanAccountNumber(String branchCode) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        String branchClean = branchCode != null ? branchCode.replaceAll("\\D", "") : "001";
        if (branchClean.length() < 3) {
            branchClean = ("000" + branchClean);
            branchClean = branchClean.substring(branchClean.length() - 3);
        } else if (branchClean.length() > 3) {
            branchClean = branchClean.substring(0, 3);
        }
        return String.format("%s2111%09d", branchClean, seq % 1000000000L);
    }
}

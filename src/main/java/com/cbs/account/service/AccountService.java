package com.cbs.account.service;

import com.cbs.account.domain.Account;
import com.cbs.account.domain.AccountIfc;
import com.cbs.account.domain.DepositProductIfcMapping;
import com.cbs.account.domain.DptProductCatalog;
import com.cbs.account.dto.AccountDetailResponse;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.repository.AccountIfcRepository;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.repository.AccountSpecification;
import com.cbs.account.repository.DepositProductIfcMappingRepository;
import com.cbs.account.repository.DptProductCatalogRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.ProductNotFoundException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * CASA Account Management Service.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AccountService {

    private final AccountRepository accountRepository;
    private final DptProductCatalogRepository dptProductCatalogRepository;
    private final CustomerRepository customerRepository;
    private final DepositProductIfcMappingRepository depositProductIfcMappingRepository;
    private final AccountIfcRepository accountIfcRepository;
    private final WorkingDateService workingDateService;
    private final CodeListService codeListService;
    private final JdbcTemplate jdbcTemplate;

    /**
     * Opens a new CASA deposit account for an existing customer.
     */
    @Transactional
    public Account openAccount(OpenAccountCommand command) {
        log.info("Opening account for customerId={}, productCode='{}', branch='{}'",
                command.customerId(), command.productCode(), command.branchCode());

        // 1. Verify customer exists
        if (!customerRepository.existsById(command.customerId())) {
            throw CustomerNotFoundException.byId(command.customerId());
        }

        // 2. Verify product catalog exists and is active
        DptProductCatalog product = dptProductCatalogRepository.findByProductCodeAndIsActive(command.productCode(), "Y")
                .orElseThrow(() -> new ProductNotFoundException(command.productCode()));

        // 3. Validate account status
        if (!codeListService.isValid("ACCT_STATUS", command.status())) {
            throw new IllegalArgumentException("Invalid ACCT_STATUS: " + command.status());
        }

        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String accountNumber = generateAccountNumber(command.branchCode(), command.productCode());

        Account account = Account.builder()
                .accountNumber(accountNumber)
                .customerId(command.customerId())
                .productCode(product.getProductCode())
                .glCode(product.getDefaultGlCode())
                .branchCode(command.branchCode().trim())
                .currency(product.getCurrency())
                .ledgerBalance(BigDecimal.ZERO.setScale(4))
                .holdBalance(BigDecimal.ZERO.setScale(4))
                .accruedInterest(BigDecimal.ZERO.setScale(4))
                .status(command.status())
                .createdWorkingDate(workingDate)
                .createdAt(OffsetDateTime.now())
                .build();

        Account saved = accountRepository.save(account);

        // Clone bundled IFC charges from Product Catalog to Account IFC
        List<DepositProductIfcMapping> mappings = depositProductIfcMappingRepository.findByProductCode(product.getProductCode());
        for (DepositProductIfcMapping mapping : mappings) {
            AccountIfc accountIfc = AccountIfc.builder()
                    .accountId(saved.getAccountId())
                    .ifcCode(mapping.getIfcCode())
                    .appliedRate(mapping.getDefaultRate())
                    .isWaived("N")
                    .effectiveFrom(workingDate)
                    .createdAt(OffsetDateTime.now())
                    .build();
            accountIfcRepository.save(accountIfc);
        }

        log.info("CASA account opened successfully. accountId={}, accountNumber='{}', glCode='{}', ifcsBound={}",
                saved.getAccountId(), saved.getAccountNumber(), saved.getGlCode(), mappings.size());
        return saved;
    }

    @Transactional(readOnly = true)
    public Account getAccount(Long accountId) {
        return accountRepository.findById(accountId)
                .orElseThrow(() -> AccountNotFoundException.byId(accountId));
    }

    @Transactional(readOnly = true)
    public Account getAccountByNumber(String accountNumber) {
        return accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> AccountNotFoundException.byNumber(accountNumber));
    }

    @Transactional(readOnly = true)
    public List<Account> getAccountsByCustomer(Long customerId) {
        return accountRepository.findByCustomerId(customerId);
    }

    @Transactional(readOnly = true)
    public List<AccountDetailResponse> searchAccounts(
            String keyword,
            String accountNumber,
            String customerName,
            String productCode,
            String status,
            String branchCode
    ) {
        Set<Long> matchedCustomerIds = new HashSet<>();
        if (customerName != null && !customerName.isBlank()) {
            List<Customer> customers = customerRepository.findByFullNameContainingIgnoreCase(customerName.trim());
            for (Customer c : customers) {
                matchedCustomerIds.add(c.getCustomerId());
            }
        }
        if (keyword != null && !keyword.isBlank()) {
            List<Customer> customers = customerRepository.findByFullNameContainingIgnoreCase(keyword.trim());
            for (Customer c : customers) {
                matchedCustomerIds.add(c.getCustomerId());
            }
        }

        Specification<Account> spec = AccountSpecification.withCriteria(
                keyword, accountNumber, matchedCustomerIds, productCode, status, branchCode
        );

        List<Account> accounts = accountRepository.findAll(spec);
        Set<Long> custIds = accounts.stream().map(Account::getCustomerId).collect(Collectors.toSet());
        Map<Long, String> custNameMap = customerRepository.findAllById(custIds).stream()
                .collect(Collectors.toMap(Customer::getCustomerId, Customer::getFullName, (existing, replacement) -> existing));

        return accounts.stream()
                .map(a -> new AccountDetailResponse(
                        a.getAccountId(),
                        a.getAccountNumber(),
                        a.getCustomerId(),
                        custNameMap.getOrDefault(a.getCustomerId(), "Customer " + a.getCustomerId()),
                        a.getGlCode(),
                        a.getCurrency(),
                        a.getLedgerBalance(),
                        a.getHoldBalance(),
                        a.getAvailableBalance(),
                        a.getStatus(),
                        a.getBranchCode(),
                        a.getProductCode()
                ))
                .toList();
    }

    private String generateAccountNumber(String branchCode, String productCode) {
        Long seq = jdbcTemplate.queryForObject("SELECT CBS_GLOBAL_SEQ.NEXTVAL FROM DUAL", Long.class);
        // Normalize branch digits
        String branchClean = branchCode.replaceAll("\\D", "");
        if (branchClean.length() < 3) {
            branchClean = ("000" + branchClean);
            branchClean = branchClean.substring(branchClean.length() - 3);
        } else if (branchClean.length() > 3) {
            branchClean = branchClean.substring(0, 3);
        }
        return String.format("%s421%010d", branchClean, seq);
    }
}

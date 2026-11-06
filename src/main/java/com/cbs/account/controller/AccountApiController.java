package com.cbs.account.controller;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.AccountDetailResponse;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.service.AccountService;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.customer.repository.CustomerRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AccountApiController {

    private final AccountService accountService;
    private final AccountRepository accountRepository;
    private final CustomerRepository customerRepository;

    @GetMapping("/search")
    public ResponseEntity<List<AccountDetailResponse>> searchAccounts(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String accountNumber,
            @RequestParam(required = false) String customerName,
            @RequestParam(required = false) String productCode,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String branchCode
    ) {
        log.info("Searching accounts: keyword='{}', accountNumber='{}', customerName='{}', productCode='{}', status='{}', branchCode='{}'",
                keyword, accountNumber, customerName, productCode, status, branchCode);
        return ResponseEntity.ok(accountService.searchAccounts(keyword, accountNumber, customerName, productCode, status, branchCode));
    }

    @GetMapping("/{accountNumber}")
    public ResponseEntity<AccountDetailResponse> getAccountByNumber(@PathVariable String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
                .orElseThrow(() -> AccountNotFoundException.byNumber(accountNumber));

        String customerName = customerRepository.findById(account.getCustomerId())
                .map(c -> c.getFullName())
                .orElse("KH-" + account.getCustomerId());

        AccountDetailResponse resp = new AccountDetailResponse(
                account.getAccountId(),
                account.getAccountNumber(),
                account.getCustomerId(),
                customerName,
                account.getGlCode(),
                account.getCurrency(),
                account.getLedgerBalance(),
                account.getHoldBalance(),
                account.getAvailableBalance(),
                account.getStatus(),
                account.getBranchCode(),
                account.getProductCode()
        );

        return ResponseEntity.ok(resp);
    }

    @GetMapping("/customer/{customerId}")
    public ResponseEntity<List<AccountDetailResponse>> getAccountsByCustomer(@PathVariable Long customerId) {
        List<Account> accounts = accountRepository.findByCustomerId(customerId);
        String customerName = customerRepository.findById(customerId)
                .map(c -> c.getFullName())
                .orElse("KH-" + customerId);

        List<AccountDetailResponse> list = accounts.stream()
                .map(a -> new AccountDetailResponse(
                        a.getAccountId(),
                        a.getAccountNumber(),
                        a.getCustomerId(),
                        customerName,
                        a.getGlCode(),
                        a.getCurrency(),
                        a.getLedgerBalance(),
                        a.getHoldBalance(),
                        a.getAvailableBalance(),
                        a.getStatus(),
                        a.getBranchCode(),
                        a.getProductCode()
                )).toList();

        return ResponseEntity.ok(list);
    }

    @PostMapping("/open")
    public ResponseEntity<Account> openAccount(@RequestBody OpenAccountCommand command) {
        return ResponseEntity.ok(accountService.openAccount(command));
    }
}

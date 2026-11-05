package com.cbs.lending.controller;

import com.cbs.common.exception.LoanAccountNotFoundException;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.dto.DisburseLoanCommand;
import com.cbs.lending.dto.LoanDisbursementReceipt;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.service.LoanAccountService;
import com.cbs.lending.service.LoanDisbursementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/lending")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class LendingApiController {

    private final LoanAccountService loanAccountService;
    private final LoanDisbursementService loanDisbursementService;
    private final LoanAccountRepository loanAccountRepository;

    @PostMapping("/contracts")
    public ResponseEntity<LoanAccount> createLoanContract(@RequestBody CreateLoanContractCommand command) {
        return ResponseEntity.ok(loanAccountService.createLoanContract(command));
    }

    @PostMapping("/disburse")
    public ResponseEntity<LoanDisbursementReceipt> disburseLoan(@RequestBody DisburseLoanCommand command) {
        String key = (command.idempotencyKey() != null && !command.idempotencyKey().isBlank())
                ? command.idempotencyKey()
                : "disb-" + UUID.randomUUID();

        String channel = (command.channelId() != null && !command.channelId().isBlank())
                ? command.channelId()
                : "BRANCH";

        DisburseLoanCommand normalized = DisburseLoanCommand.builder()
                .loanId(command.loanId())
                .channelId(channel)
                .idempotencyKey(key)
                .build();

        return ResponseEntity.ok(loanDisbursementService.disburse(normalized));
    }

    @GetMapping("/{loanId}")
    public ResponseEntity<LoanAccount> getLoan(@PathVariable Long loanId) {
        return loanAccountRepository.findById(loanId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new LoanAccountNotFoundException(loanId));
    }

    @GetMapping("/{loanId}/schedules")
    public ResponseEntity<List<LoanSchedule>> getSchedules(@PathVariable Long loanId) {
        return ResponseEntity.ok(loanAccountService.getLoanSchedule(loanId));
    }
}

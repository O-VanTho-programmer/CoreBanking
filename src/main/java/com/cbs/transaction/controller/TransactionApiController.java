package com.cbs.transaction.controller;

import com.cbs.transaction.dto.*;
import com.cbs.transaction.service.TransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/transactions")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class TransactionApiController {

    private final TransactionService transactionService;

    @PostMapping("/deposit")
    public ResponseEntity<TransactionReceipt> deposit(@RequestBody DepositCommand command) {
        String key = (command.idempotencyKey() != null && !command.idempotencyKey().isBlank())
                ? command.idempotencyKey()
                : "dep-" + UUID.randomUUID();

        String channel = (command.channelId() != null && !command.channelId().isBlank())
                ? command.channelId()
                : "BRANCH";

        String branch = (command.branchCode() != null && !command.branchCode().isBlank())
                ? command.branchCode()
                : "VN001";

        DepositCommand normalized = DepositCommand.builder()
                .channelId(channel)
                .idempotencyKey(key)
                .accountNumber(command.accountNumber())
                .amount(command.amount())
                .currency(command.currency())
                .branchCode(branch)
                .narration(command.narration())
                .makerId(command.makerId() != null ? command.makerId() : "TELLER01")
                .build();

        return ResponseEntity.ok(transactionService.deposit(normalized));
    }

    @PostMapping("/withdraw")
    public ResponseEntity<TransactionReceipt> withdraw(@RequestBody WithdrawCommand command) {
        String key = (command.idempotencyKey() != null && !command.idempotencyKey().isBlank())
                ? command.idempotencyKey()
                : "wdr-" + UUID.randomUUID();

        String channel = (command.channelId() != null && !command.channelId().isBlank())
                ? command.channelId()
                : "BRANCH";

        String branch = (command.branchCode() != null && !command.branchCode().isBlank())
                ? command.branchCode()
                : "VN001";

        WithdrawCommand normalized = WithdrawCommand.builder()
                .channelId(channel)
                .idempotencyKey(key)
                .accountNumber(command.accountNumber())
                .amount(command.amount())
                .currency(command.currency())
                .branchCode(branch)
                .narration(command.narration())
                .makerId(command.makerId() != null ? command.makerId() : "TELLER01")
                .build();

        return ResponseEntity.ok(transactionService.withdraw(normalized));
    }

    @PostMapping("/transfer")
    public ResponseEntity<TransactionReceipt> transfer(@RequestBody TransferCommand command) {
        String key = (command.idempotencyKey() != null && !command.idempotencyKey().isBlank())
                ? command.idempotencyKey()
                : "trf-" + UUID.randomUUID();

        String channel = (command.channelId() != null && !command.channelId().isBlank())
                ? command.channelId()
                : "BRANCH";

        TransferCommand normalized = TransferCommand.builder()
                .channelId(channel)
                .idempotencyKey(key)
                .fromAccountNumber(command.fromAccountNumber())
                .toAccountNumber(command.toAccountNumber())
                .amount(command.amount())
                .feeAmount(command.feeAmount())
                .currency(command.currency())
                .narration(command.narration())
                .makerId(command.makerId() != null ? command.makerId() : "TELLER01")
                .build();

        return ResponseEntity.ok(transactionService.transfer(normalized));
    }

    @PostMapping("/reverse")
    public ResponseEntity<TransactionReceipt> reverse(@RequestBody ReverseCommand command) {
        String key = (command.idempotencyKey() != null && !command.idempotencyKey().isBlank())
                ? command.idempotencyKey()
                : "rev-" + UUID.randomUUID();

        String channel = (command.channelId() != null && !command.channelId().isBlank())
                ? command.channelId()
                : "BRANCH";

        ReverseCommand normalized = ReverseCommand.builder()
                .channelId(channel)
                .idempotencyKey(key)
                .originalTrxNumber(command.originalTrxNumber())
                .reason(command.reason())
                .makerId(command.makerId() != null ? command.makerId() : "TELLER01")
                .build();

        return ResponseEntity.ok(transactionService.reverse(normalized));
    }
}

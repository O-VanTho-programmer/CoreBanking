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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransactionServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Mock
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Mock
    private PostingEngineService postingEngineService;

    @Mock
    private IdempotencyService idempotencyService;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private TransactionService transactionService;

    private final LocalDate workingDate = LocalDate.of(2026, 10, 22);

    @BeforeEach
    void setUp() {
        lenient().when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        lenient().when(workingDateService.isSystemCutoff()).thenReturn(false);
        lenient().when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(12345L);
    }

    @Test
    @DisplayName("Transfer: Idempotent replay when transaction already exists")
    void shouldReturnExistingReceiptWhenIdempotentReplay() {
        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-key-001")
                .fromAccountNumber("1000000000000001")
                .toAccountNumber("1000000000000002")
                .amount(new BigDecimal("500000.0000"))
                .build();

        TransactionReceipt existingReceipt = TransactionReceipt.builder()
                .trxId(999L)
                .trxNumber("TRX-20261022-00000999")
                .status("COMPLETED")
                .build();

        when(idempotencyService.checkExisting("MB", "idemp-key-001")).thenReturn(Optional.of(existingReceipt));

        TransactionReceipt result = transactionService.transfer(cmd);

        assertThat(result).isSameAs(existingReceipt);
        verifyNoInteractions(accountRepository);
        verifyNoInteractions(postingEngineService);
    }

    @Test
    @DisplayName("Transfer: Rejected when system is in Cutoff status (Invariant 4)")
    void shouldThrowExceptionWhenSystemIsInCutoff() {
        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-key-002")
                .fromAccountNumber("1000000000000001")
                .toAccountNumber("1000000000000002")
                .amount(new BigDecimal("500000.0000"))
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());
        when(workingDateService.isSystemCutoff()).thenReturn(true);

        assertThatThrownBy(() -> transactionService.transfer(cmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cutoff");
    }

    @Test
    @DisplayName("Transfer: Throws InsufficientFundsException when debited account has insufficient available balance (Invariant 2)")
    void shouldThrowInsufficientFundsWhenAvailableBalanceTooLow() {
        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-key-003")
                .fromAccountNumber("1000000000000001")
                .toAccountNumber("1000000000000002")
                .amount(new BigDecimal("1000000.0000"))
                .currency("VND")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());

        Account fromAcc = Account.builder()
                .accountId(1L)
                .accountNumber("1000000000000001")
                .status("ACTIVE")
                .ledgerBalance(new BigDecimal("500000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        Account toAcc = Account.builder()
                .accountId(2L)
                .accountNumber("1000000000000002")
                .status("ACTIVE")
                .ledgerBalance(BigDecimal.ZERO)
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        when(accountRepository.findIdByAccountNumber("1000000000000001")).thenReturn(Optional.of(1L));
        when(accountRepository.findIdByAccountNumber("1000000000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findAndLockAccountsByIds(List.of(1L, 2L))).thenReturn(List.of(fromAcc, toAcc));

        PostingResult postingResult = new PostingResult(
                "TRF_INTERNAL",
                workingDate,
                "VND",
                new BigDecimal("1000000.0000"),
                new BigDecimal("1000000.0000"),
                List.of(
                        new JournalLeg(1, 1, 1L, "4211", "D", new BigDecimal("1000000.0000"), "VND", workingDate, "Debit"),
                        new JournalLeg(1, 2, 2L, "4211", "C", new BigDecimal("1000000.0000"), "VND", workingDate, "Credit")
                )
        );

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);

        assertThatThrownBy(() -> transactionService.transfer(cmd))
                .isInstanceOf(InsufficientFundsException.class);
    }

    @Test
    @DisplayName("Transfer: Successful internal transfer with early IN_FLIGHT claim and COMPLETED finish")
    void shouldExecuteTransferSuccessfully() {
        TransferCommand cmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("idemp-key-004")
                .fromAccountNumber("1000000000000002")
                .toAccountNumber("1000000000000001") // Note: from 2 to 1 (testing ordering)
                .amount(new BigDecimal("2000000.0000"))
                .currency("VND")
                .makerId("teller01")
                .narration("Chuyen tien mua laptop")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());

        Account acc1 = Account.builder()
                .accountId(1L)
                .accountNumber("1000000000000001")
                .status("ACTIVE")
                .ledgerBalance(new BigDecimal("1000000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        Account acc2 = Account.builder()
                .accountId(2L)
                .accountNumber("1000000000000002")
                .status("ACTIVE")
                .ledgerBalance(new BigDecimal("5000000.0000"))
                .holdBalance(new BigDecimal("500000.0000")) // available = 4,500,000
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        when(accountRepository.findIdByAccountNumber("1000000000000002")).thenReturn(Optional.of(2L));
        when(accountRepository.findIdByAccountNumber("1000000000000001")).thenReturn(Optional.of(1L));

        // Lock ordering MUST be [1L, 2L]
        when(accountRepository.findAndLockAccountsByIds(List.of(1L, 2L))).thenReturn(List.of(acc1, acc2));

        PostingResult postingResult = new PostingResult(
                "TRF_INTERNAL",
                workingDate,
                "VND",
                new BigDecimal("2000000.0000"),
                new BigDecimal("2000000.0000"),
                List.of(
                        new JournalLeg(1, 1, 2L, "4211", "D", new BigDecimal("2000000.0000"), "VND", workingDate, "Transfer debit"),
                        new JournalLeg(1, 2, 1L, "4211", "C", new BigDecimal("2000000.0000"), "VND", workingDate, "Transfer credit")
                )
        );

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);

        TrxJournalActive savedInFlight = TrxJournalActive.builder()
                .trxId(555L)
                .trxNumber("TRX-20261022-00012345")
                .status("IN_FLIGHT")
                .createdAt(OffsetDateTime.now())
                .build();

        when(trxJournalActiveRepository.saveAndFlush(any(TrxJournalActive.class))).thenReturn(savedInFlight);

        TransactionReceipt receipt = transactionService.transfer(cmd);

        assertThat(receipt).isNotNull();
        assertThat(receipt.status()).isEqualTo("COMPLETED");
        assertThat(receipt.trxNumber()).isEqualTo("TRX-20261022-00012345");

        // Verify balance updates
        // acc2 debited 2M: ledger 5M -> 3M, available 4.5M -> 2.5M
        assertThat(acc2.getLedgerBalance()).isEqualByComparingTo("3000000.0000");
        assertThat(acc2.getAvailableBalance()).isEqualByComparingTo("2500000.0000");

        // acc1 credited 2M: ledger 1M -> 3M, available 1M -> 3M
        assertThat(acc1.getLedgerBalance()).isEqualByComparingTo("3000000.0000");
        assertThat(acc1.getAvailableBalance()).isEqualByComparingTo("3000000.0000");

        // Verify journal header created in-flight then marked completed
        ArgumentCaptor<TrxJournalActive> headerCaptor = ArgumentCaptor.forClass(TrxJournalActive.class);
        verify(trxJournalActiveRepository).saveAndFlush(headerCaptor.capture());
        assertThat(headerCaptor.getValue().getStatus()).isEqualTo("IN_FLIGHT");
        assertThat(headerCaptor.getValue().getTrxCode()).isEqualTo("TRF_INTERNAL");

        verify(trxJournalActiveRepository).save(savedInFlight);
        assertThat(savedInFlight.getStatus()).isEqualTo("COMPLETED");

        // Verify journal legs saved
        verify(journalEntryActiveRepository, times(2)).save(any(JournalEntryActive.class));
        verify(idempotencyService).recordCompletion(eq("MB"), eq("idemp-key-004"), any(TransactionReceipt.class));
    }

    @Test
    @DisplayName("Deposit: Counter cash deposit increases account balance")
    void shouldExecuteCashDepositSuccessfully() {
        DepositCommand cmd = DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("idemp-dep-001")
                .accountNumber("1000000000000001")
                .amount(new BigDecimal("10000000.0000"))
                .branchCode("VN001")
                .currency("VND")
                .makerId("teller01")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());

        Account acc = Account.builder()
                .accountId(1L)
                .accountNumber("1000000000000001")
                .status("ACTIVE")
                .ledgerBalance(new BigDecimal("500000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        when(accountRepository.findIdByAccountNumber("1000000000000001")).thenReturn(Optional.of(1L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));

        PostingResult postingResult = new PostingResult(
                "CASH_DEP",
                workingDate,
                "VND",
                new BigDecimal("10000000.0000"),
                new BigDecimal("10000000.0000"),
                List.of(
                        new JournalLeg(1, 1, null, "1011", "D", new BigDecimal("10000000.0000"), "VND", workingDate, "Cash Vault"),
                        new JournalLeg(1, 2, 1L, "4211", "C", new BigDecimal("10000000.0000"), "VND", workingDate, "Customer CASA")
                )
        );

        when(postingEngineService.generateAndValidateEntries(any(PostingRequest.class))).thenReturn(postingResult);

        TrxJournalActive savedInFlight = TrxJournalActive.builder()
                .trxId(777L)
                .trxNumber("TRX-20261022-00012345")
                .status("IN_FLIGHT")
                .createdAt(OffsetDateTime.now())
                .build();
        when(trxJournalActiveRepository.saveAndFlush(any())).thenReturn(savedInFlight);

        TransactionReceipt receipt = transactionService.deposit(cmd);

        assertThat(receipt).isNotNull();
        assertThat(receipt.status()).isEqualTo("COMPLETED");
        assertThat(acc.getLedgerBalance()).isEqualByComparingTo("10500000.0000");
        assertThat(acc.getAvailableBalance()).isEqualByComparingTo("10500000.0000");
    }

    @Test
    @DisplayName("Withdraw: Throws InsufficientFundsException when available balance insufficient")
    void shouldThrowInsufficientFundsOnWithdrawal() {
        WithdrawCommand cmd = WithdrawCommand.builder()
                .channelId("ATM")
                .idempotencyKey("idemp-wdr-001")
                .accountNumber("1000000000000001")
                .amount(new BigDecimal("5000000.0000"))
                .branchCode("VN001")
                .build();

        when(idempotencyService.checkExisting(any(), any())).thenReturn(Optional.empty());

        Account acc = Account.builder()
                .accountId(1L)
                .accountNumber("1000000000000001")
                .status("ACTIVE")
                .ledgerBalance(new BigDecimal("2000000.0000"))
                .holdBalance(BigDecimal.ZERO)
                .currency("VND")
                .glCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        when(accountRepository.findIdByAccountNumber("1000000000000001")).thenReturn(Optional.of(1L));
        when(accountRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(acc));

        PostingResult postingResult = new PostingResult(
                "CASH_WDR",
                workingDate,
                "VND",
                new BigDecimal("5000000.0000"),
                new BigDecimal("5000000.0000"),
                List.of(
                        new JournalLeg(1, 1, 1L, "4211", "D", new BigDecimal("5000000.0000"), "VND", workingDate, "Customer CASA"),
                        new JournalLeg(1, 2, null, "1011", "C", new BigDecimal("5000000.0000"), "VND", workingDate, "Cash Vault")
                )
        );
        when(postingEngineService.generateAndValidateEntries(any())).thenReturn(postingResult);

        assertThatThrownBy(() -> transactionService.withdraw(cmd))
                .isInstanceOf(InsufficientFundsException.class);
    }
}

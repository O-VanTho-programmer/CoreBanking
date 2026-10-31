package com.cbs.eod;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.repository.AccountRepository;
import com.cbs.account.service.AccountService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.exception.InFlightTransactionFoundException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.eod.dto.ArchivalBatchResult;
import com.cbs.eod.service.DataArchivalService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.JournalEntryDone;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.domain.TrxJournalDone;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.ReverseCommand;
import com.cbs.transaction.dto.TransactionReceipt;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.JournalEntryDoneRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import com.cbs.transaction.repository.TrxJournalDoneRepository;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class DataArchivalIntegrationTest {

    @Autowired
    private DataArchivalService dataArchivalService;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Autowired
    private TrxJournalDoneRepository trxJournalDoneRepository;

    @Autowired
    private JournalEntryDoneRepository journalEntryDoneRepository;

    @Autowired
    private WorkingDateService workingDateService;

    @Test
    @DisplayName("Integration: End-to-end MoveDataHis moves transactions and preserves reversal lineage in DONE")
    void testEndToEndDataArchival() {
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customers & Accounts
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDA-" + uniqueSuffix)
                .fullName("Archival A " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("IDB-" + uniqueSuffix)
                .fullName("Archival B " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account acctA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account acctB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // 2. Deposit funds into Account A
        TransactionReceipt depReceipt = transactionService.deposit(DepositCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("DEP-" + uniqueSuffix)
                .accountNumber(acctA.getAccountNumber())
                .amount(new BigDecimal("10000000.0000"))
                .makerId("TELLER01")
                .narration("Deposit for archival test")
                .build());

        // 3. Transfer from A to B
        TransactionReceipt trfReceipt = transactionService.transfer(TransferCommand.builder()
                .channelId("MOBILE")
                .idempotencyKey("TRF-" + uniqueSuffix)
                .fromAccountNumber(acctA.getAccountNumber())
                .toAccountNumber(acctB.getAccountNumber())
                .amount(new BigDecimal("2000000.0000"))
                .feeAmount(new BigDecimal("5000.0000"))
                .makerId("CUST_APP")
                .narration("Transfer for archival test")
                .build());

        // 4. Reverse the transfer (creates reversal with original_trx_id pointing to transfer)
        TransactionReceipt revReceipt = transactionService.reverse(ReverseCommand.builder()
                .channelId("BRANCH")
                .idempotencyKey("REV-" + uniqueSuffix)
                .originalTrxNumber(trfReceipt.trxNumber())
                .reason("Mistake reversal for archival")
                .makerId("TELLER02")
                .build());

        Long depTrxId = depReceipt.trxId();
        Long trfTrxId = trfReceipt.trxId();
        Long revTrxId = revReceipt.trxId();

        // Verify active tables currently hold these transactions
        assertThat(trxJournalActiveRepository.findById(depTrxId)).isPresent();
        assertThat(trxJournalActiveRepository.findById(trfTrxId)).isPresent();
        assertThat(trxJournalActiveRepository.findById(revTrxId)).isPresent();

        // 5. Execute Data Archival (MoveDataHis)
        ArchivalBatchResult result = dataArchivalService.archiveCompletedTransactions(workingDate);

        assertThat(result.workingDate()).isEqualTo(workingDate);
        assertThat(result.archivedTransactionsCount()).isGreaterThanOrEqualTo(3);
        assertThat(result.archivedEntriesCount()).isGreaterThanOrEqualTo(8); // dep (2) + trf (4) + rev (4)

        // 6. Verify Cold Storage (TRX_JOURNAL_DONE & JOURNAL_ENTRY_DONE)
        TrxJournalDone archivedDep = trxJournalDoneRepository.findById(depTrxId).orElseThrow();
        TrxJournalDone archivedTrf = trxJournalDoneRepository.findById(trfTrxId).orElseThrow();
        TrxJournalDone archivedRev = trxJournalDoneRepository.findById(revTrxId).orElseThrow();

        assertThat(archivedDep.getStatus()).isEqualTo("COMPLETED");
        assertThat(archivedTrf.getStatus()).isEqualTo("COMPLETED");
        assertThat(archivedTrf.getIsReversed()).isEqualTo("Y");
        assertThat(archivedRev.getStatus()).isEqualTo("COMPLETED");
        // Reversal lineage must be preserved in cold storage!
        assertThat(archivedRev.getOriginalTrxId()).isEqualTo(trfTrxId);
        assertThat(archivedRev.getReversalReason()).isEqualTo("Mistake reversal for archival");

        List<JournalEntryDone> depEntries = journalEntryDoneRepository.findByTrxId(depTrxId);
        List<JournalEntryDone> trfEntries = journalEntryDoneRepository.findByTrxId(trfTrxId);
        List<JournalEntryDone> revEntries = journalEntryDoneRepository.findByTrxId(revTrxId);

        assertThat(depEntries).hasSize(2);
        assertThat(trfEntries).hasSize(4);
        assertThat(revEntries).hasSize(4);

        // 7. Verify Hot Storage is completely cleaned up
        assertThat(trxJournalActiveRepository.findById(depTrxId)).isEmpty();
        assertThat(trxJournalActiveRepository.findById(trfTrxId)).isEmpty();
        assertThat(trxJournalActiveRepository.findById(revTrxId)).isEmpty();

        assertThat(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(depTrxId)).isEmpty();
        assertThat(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(trfTrxId)).isEmpty();
        assertThat(journalEntryActiveRepository.findByTrxIdOrderByEntryGroupAscEntryIndexAsc(revTrxId)).isEmpty();
    }

    @Test
    @DisplayName("Integration: IN_FLIGHT transaction halts archival and throws InFlightTransactionFoundException")
    void testInFlightTransactionBlocksArchival() {
        LocalDate workingDate = workingDateService.getCurrentWorkingDate();
        String inFlightKey = "INFLIGHT-" + UUID.randomUUID().toString().substring(0, 8);

        TrxJournalActive inFlightTrx = TrxJournalActive.builder()
                .trxNumber("TRX-INFLIGHT-" + System.currentTimeMillis() % 100000000)
                .channelId("MOBILE")
                .idempotencyKey(inFlightKey)
                .trxCode("TRF_INTERNAL")
                .workingDate(workingDate)
                .status("IN_FLIGHT")
                .makerId("MAKER_TEST")
                .isReversed("N")
                .createdAt(OffsetDateTime.now())
                .build();

        inFlightTrx = trxJournalActiveRepository.saveAndFlush(inFlightTrx);

        try {
            assertThatThrownBy(() -> dataArchivalService.archiveCompletedTransactions(workingDate))
                    .isInstanceOf(InFlightTransactionFoundException.class)
                    .hasMessageContaining("Cannot archive data: Found");
        } finally {
            trxJournalActiveRepository.delete(inFlightTrx);
        }
    }
}

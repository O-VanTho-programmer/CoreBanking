package com.cbs.lending;

import com.cbs.deposit.domain.Account;
import com.cbs.deposit.dto.OpenAccountCommand;
import com.cbs.deposit.repository.AccountRepository;
import com.cbs.deposit.service.AccountService;
import com.cbs.common.exception.LoanAlreadyDisbursedException;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.dto.DisburseLoanCommand;
import com.cbs.lending.dto.LoanDisbursementReceipt;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.service.LoanAccountService;
import com.cbs.lending.service.LoanDisbursementService;
import com.cbs.transaction.domain.JournalEntryActive;
import com.cbs.transaction.domain.TrxJournalActive;
import com.cbs.transaction.repository.JournalEntryActiveRepository;
import com.cbs.transaction.repository.TrxJournalActiveRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class LoanDisbursementIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LoanAccountService loanAccountService;

    @Autowired
    private LoanDisbursementService loanDisbursementService;

    @Autowired
    private LoanAccountRepository loanAccountRepository;

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TrxJournalActiveRepository trxJournalActiveRepository;

    @Autowired
    private JournalEntryActiveRepository journalEntryActiveRepository;

    @Test
    @DisplayName("Integration: End-to-End Loan Creation, Disbursement via Posting Matrix, and Balance/Lineage Verification")
    void testE2ELoanDisbursementFlow() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Borrower Customer
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("ID-DISB-" + uniqueSuffix)
                .fullName("Borrower " + uniqueSuffix)
                .customerType("INDIVIDUAL")
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        // 2. Open Linked CASA Account
        Account casaAccount = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(customer.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        assertThat(casaAccount.getLedgerBalance()).isEqualByComparingTo(BigDecimal.ZERO);

        // 3. Create Loan Contract (50,000,000 VND, 12 months, APPROVED)
        LoanAccount loan = loanAccountService.createLoanContract(CreateLoanContractCommand.builder()
                .customerId(customer.getCustomerId())
                .linkedCasaAccountId(casaAccount.getAccountId())
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .termMonths(12)
                .firstDueDate(LocalDate.now().plusMonths(1))
                .build());

        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.APPROVED.name());
        assertThat(loan.getDisbursedAt()).isNull();

        // 4. Execute Loan Disbursement
        String idempKey = "disb-key-" + uniqueSuffix;
        DisburseLoanCommand disburseCmd = DisburseLoanCommand.builder()
                .loanId(loan.getLoanId())
                .channelId("BRANCH")
                .idempotencyKey(idempKey)
                .branchCode("VN001")
                .makerId("TELLER01")
                .narrationOverride("Giai ngan khoan vay tin chap tieu dung")
                .build();

        LoanDisbursementReceipt receipt = loanDisbursementService.disburse(disburseCmd);

        assertThat(receipt).isNotNull();
        assertThat(receipt.trxNumber()).startsWith("TRX-");
        assertThat(receipt.status()).isEqualTo("COMPLETED");
        assertThat(receipt.disbursedAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(receipt.casaAvailableBalance()).isEqualByComparingTo("50000000.0000");

        // 5. Verify CASA Balance Mutation
        Account updatedCasa = accountRepository.findById(casaAccount.getAccountId()).orElseThrow();
        assertThat(updatedCasa.getLedgerBalance()).isEqualByComparingTo("50000000.0000");
        assertThat(updatedCasa.getAvailableBalance()).isEqualByComparingTo("50000000.0000");

        // 6. Verify Loan State Transition
        LoanAccount updatedLoan = loanAccountRepository.findById(loan.getLoanId()).orElseThrow();
        assertThat(updatedLoan.getLoanStatus()).isEqualTo(LoanStatus.ACTIVE.name());
        assertThat(updatedLoan.getDisbursedAt()).isNotNull();

        // 7. Verify Accounting Journal & Solution 1 Lineage
        TrxJournalActive journal = trxJournalActiveRepository.findById(receipt.trxId()).orElseThrow();
        assertThat(journal.getTrxCode()).isEqualTo("LOAN_DISBURSE");
        assertThat(journal.getStatus()).isEqualTo("COMPLETED");

        List<JournalEntryActive> entries = journalEntryActiveRepository.findByTrxId(journal.getTrxId());
        assertThat(entries).hasSize(2);

        // Leg 1: DR 2111 with loan_id
        JournalEntryActive debitLeg = entries.stream()
                .filter(e -> "D".equals(e.getDrCr()))
                .findFirst().orElseThrow();
        assertThat(debitLeg.getGlCode()).isEqualTo("2111");
        assertThat(debitLeg.getAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(debitLeg.getLoanId()).isEqualTo(loan.getLoanId());
        assertThat(debitLeg.getAccountId()).isNull();

        // Leg 2: CR 4211 with account_id
        JournalEntryActive creditLeg = entries.stream()
                .filter(e -> "C".equals(e.getDrCr()))
                .findFirst().orElseThrow();
        assertThat(creditLeg.getGlCode()).isEqualTo("4211");
        assertThat(creditLeg.getAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(creditLeg.getAccountId()).isEqualTo(casaAccount.getAccountId());
        assertThat(creditLeg.getLoanId()).isNull();

        // 8. Test Idempotency Retry: Calling again with same idempKey returns cached receipt
        LoanDisbursementReceipt replayedReceipt = loanDisbursementService.disburse(disburseCmd);
        assertThat(replayedReceipt.trxNumber()).isEqualTo(receipt.trxNumber());
        assertThat(replayedReceipt.disbursedAmount()).isEqualByComparingTo("50000000.0000");

        // Balance must remain unchanged (50,000,000, NOT 100,000,000)
        Account replayedCasa = accountRepository.findById(casaAccount.getAccountId()).orElseThrow();
        assertThat(replayedCasa.getAvailableBalance()).isEqualByComparingTo("50000000.0000");

        // 9. Test Re-disbursement with new idempKey throws LoanAlreadyDisbursedException
        DisburseLoanCommand secondCmd = DisburseLoanCommand.builder()
                .loanId(loan.getLoanId())
                .channelId("BRANCH")
                .idempotencyKey("disb-new-key-" + uniqueSuffix)
                .build();

        assertThatThrownBy(() -> loanDisbursementService.disburse(secondCmd))
                .isInstanceOf(LoanAlreadyDisbursedException.class)
                .hasMessageContaining("already in 'ACTIVE' status");
    }
}

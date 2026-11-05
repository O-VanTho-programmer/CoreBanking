package com.cbs.lending;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.service.AccountService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.domain.LoanScheduleStatus;
import com.cbs.lending.domain.LoanStatus;
import com.cbs.lending.domain.RepaymentType;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.service.LoanAccountService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class LoanIntegrationTest {

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private LoanAccountService loanAccountService;

    @Test
    @DisplayName("Integration: Open Consumer Loan with Annuity/EMI schedule and verify database persistence")
    void testOpenConsumerLoanWithAnnuityEmi() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customer
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("ID-LN-" + uniqueSuffix)
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

        LocalDate firstDueDate = LocalDate.now().plusMonths(1);

        // 3. Create Loan Contract (50,000,000 VND, 12 months, ANNUITY_EMI)
        LoanAccount loan = loanAccountService.createLoanContract(CreateLoanContractCommand.builder()
                .customerId(customer.getCustomerId())
                .linkedCasaAccountId(casaAccount.getAccountId())
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .termMonths(12)
                .firstDueDate(firstDueDate)
                .build());

        assertThat(loan.getLoanId()).isNotNull();
        assertThat(loan.getLoanAccountNumber()).matches("^[0-9]{16}$");
        assertThat(loan.getLoanAccountNumber()).contains("2111");
        assertThat(loan.getLoanStatus()).isEqualTo(LoanStatus.APPROVED.name());
        assertThat(loan.getPrincipalAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(loan.getRemainingPrincipal()).isEqualByComparingTo("50000000.0000");
        assertThat(loan.getAnnualInterestRate()).isEqualByComparingTo("0.1200"); // 9.5% base + 2.5% margin
        assertThat(loan.getOverdueInterestRate()).isEqualByComparingTo("0.1800"); // 12.0% * 1.50
        assertThat(loan.getRepaymentType()).isEqualTo(RepaymentType.ANNUITY_EMI.name());
        assertThat(loan.getMaturityDate()).isEqualTo(firstDueDate.plusMonths(11));

        // 4. Verify LOAN_SCHEDULE generated in Database
        List<LoanSchedule> schedules = loanAccountService.getLoanSchedule(loan.getLoanId());
        assertThat(schedules).hasSize(12);

        BigDecimal sumPrincipal = BigDecimal.ZERO;
        for (int i = 0; i < 12; i++) {
            LoanSchedule s = schedules.get(i);
            assertThat(s.getPeriodNumber()).isEqualTo(i + 1);
            assertThat(s.getDueDate()).isEqualTo(firstDueDate.plusMonths(i));
            assertThat(s.getStatus()).isEqualTo(LoanScheduleStatus.PENDING.name());
            assertThat(s.getPrincipalDue()).isGreaterThan(BigDecimal.ZERO);
            assertThat(s.getInterestDue()).isGreaterThan(BigDecimal.ZERO);
            assertThat(s.getTotalInstallment()).isEqualByComparingTo(s.getPrincipalDue().add(s.getInterestDue()));

            sumPrincipal = sumPrincipal.add(s.getPrincipalDue());
        }

        // Mathematical invariant: Sum of principal dues strictly equals initial loan principal
        assertThat(sumPrincipal).isEqualByComparingTo(loan.getPrincipalAmount());
    }

    @Test
    @DisplayName("Integration: Open Mortgage Loan with Equal Principal schedule and verify 24 periods")
    void testOpenMortgageLoanWithEqualPrincipal() {
        String uniqueSuffix = UUID.randomUUID().toString().substring(0, 8);

        // 1. Create Customer
        Customer customer = customerService.createCustomer(CreateCustomerCommand.builder()
                .nationalId("ID-MTG-" + uniqueSuffix)
                .fullName("Mortgage Borrower " + uniqueSuffix)
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

        LocalDate firstDueDate = LocalDate.of(2027, 1, 15);

        // 3. Create Mortgage Loan (1,200,000,000 VND, 24 months, EQUAL_PRINCIPAL)
        LoanAccount loan = loanAccountService.createLoanContract(CreateLoanContractCommand.builder()
                .customerId(customer.getCustomerId())
                .linkedCasaAccountId(casaAccount.getAccountId())
                .productCode("LN_MORTGAGE_VND")
                .principalAmount(new BigDecimal("1200000000.0000"))
                .termMonths(24)
                .firstDueDate(firstDueDate)
                .build());

        assertThat(loan.getLoanId()).isNotNull();
        assertThat(loan.getLoanAccountNumber()).matches("^[0-9]{16}$");
        assertThat(loan.getLoanAccountNumber()).contains("2111");
        assertThat(loan.getRepaymentType()).isEqualTo(RepaymentType.EQUAL_PRINCIPAL.name());
        assertThat(loan.getAnnualInterestRate()).isEqualByComparingTo("0.1150"); // 8.0% base + 3.5% margin
        assertThat(loan.getOverdueInterestRate()).isEqualByComparingTo("0.1725"); // 11.5% * 1.50

        List<LoanSchedule> schedules = loanAccountService.getLoanSchedule(loan.getLoanId());
        assertThat(schedules).hasSize(24);

        // 1,200,000,000 / 24 = 50,000,000 per month
        BigDecimal expectedMonthlyPrincipal = new BigDecimal("50000000.0000");
        BigDecimal sumPrincipal = BigDecimal.ZERO;

        for (LoanSchedule s : schedules) {
            assertThat(s.getPrincipalDue()).isEqualByComparingTo(expectedMonthlyPrincipal);
            sumPrincipal = sumPrincipal.add(s.getPrincipalDue());
        }

        assertThat(sumPrincipal).isEqualByComparingTo(loan.getPrincipalAmount());
    }
}

package com.cbs.lending.service;

import com.cbs.account.domain.Account;
import com.cbs.account.repository.AccountRepository;
import com.cbs.common.exception.AccountNotFoundException;
import com.cbs.common.exception.CustomerNotFoundException;
import com.cbs.common.exception.InvalidLoanParameterException;
import com.cbs.common.exception.LoanProductNotFoundException;
import com.cbs.customer.repository.CustomerRepository;
import com.cbs.lending.domain.CreditProductCatalog;
import com.cbs.lending.domain.LoanAccount;
import com.cbs.lending.domain.LoanSchedule;
import com.cbs.lending.dto.AmortizationResult;
import com.cbs.lending.dto.AmortizationScheduleItem;
import com.cbs.lending.dto.CreateLoanContractCommand;
import com.cbs.lending.repository.CreditProductCatalogRepository;
import com.cbs.lending.repository.LoanAccountRepository;
import com.cbs.lending.repository.LoanScheduleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class LoanAccountServiceTest {

    @Mock
    private LoanAccountRepository loanAccountRepository;

    @Mock
    private LoanScheduleRepository loanScheduleRepository;

    @Mock
    private CreditProductCatalogRepository creditProductCatalogRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AmortizationScheduleService amortizationScheduleService;

    @Mock
    private JdbcTemplate jdbcTemplate;

    @InjectMocks
    private LoanAccountService loanAccountService;

    private CreditProductCatalog consumerProduct;
    private Account casaAccount;
    private final LocalDate testDueDate = LocalDate.of(2026, 12, 1);

    @BeforeEach
    void setUp() {
        consumerProduct = CreditProductCatalog.builder()
                .productCode("LN_CONSUMER_VND")
                .productName("Vay tieu dung tin chap ca nhan VND")
                .principalGlCode("2111")
                .interestRevenueGlCode("7020")
                .interestReceivableGlCode("3911")
                .overduePrincipalGlCode("2112")
                .baseInterestRate(new BigDecimal("0.0950"))
                .overdueRateMultiplier(new BigDecimal("1.50"))
                .minPrincipalAmount(new BigDecimal("5000000.0000"))
                .maxPrincipalAmount(new BigDecimal("500000000.0000"))
                .minTermMonths(3)
                .maxTermMonths(60)
                .defaultRepaymentType("ANNUITY_EMI")
                .isActive("Y")
                .build();

        casaAccount = Account.builder()
                .accountId(200L)
                .accountNumber("0014210000000200")
                .customerId(100L)
                .status("ACTIVE")
                .build();
    }

    @Test
    @DisplayName("Create Loan Contract: Success with default product parameters and schedule generation")
    void testCreateLoanContractSuccess() {
        CreateLoanContractCommand cmd = CreateLoanContractCommand.builder()
                .customerId(100L)
                .linkedCasaAccountId(200L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .termMonths(12)
                .firstDueDate(testDueDate)
                .build();

        when(customerRepository.existsById(100L)).thenReturn(true);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(casaAccount));
        when(creditProductCatalogRepository.findByProductCodeAndIsActive("LN_CONSUMER_VND", "Y"))
                .thenReturn(Optional.of(consumerProduct));
        when(jdbcTemplate.queryForObject(anyString(), eq(Long.class))).thenReturn(5555L);

        when(loanAccountRepository.save(any(LoanAccount.class))).thenAnswer(invocation -> {
            LoanAccount a = invocation.getArgument(0);
            if (a.getLoanId() == null) a.setLoanId(888L);
            return a;
        });

        AmortizationScheduleItem item1 = AmortizationScheduleItem.builder()
                .periodNumber(1).dueDate(testDueDate)
                .principalDue(new BigDecimal("4000000.0000"))
                .interestDue(new BigDecimal("395833.3333"))
                .totalInstallment(new BigDecimal("4395833.3333"))
                .endingBalance(new BigDecimal("46000000.0000"))
                .build();

        AmortizationScheduleItem item12 = AmortizationScheduleItem.builder()
                .periodNumber(12).dueDate(testDueDate.plusMonths(11))
                .principalDue(new BigDecimal("4361245.0000"))
                .interestDue(new BigDecimal("34588.3333"))
                .totalInstallment(new BigDecimal("4395833.3333"))
                .endingBalance(BigDecimal.ZERO)
                .build();

        when(amortizationScheduleService.calculateSchedule(any(), any(), eq(12), eq("ANNUITY_EMI"), eq(testDueDate)))
                .thenReturn(AmortizationResult.builder()
                        .repaymentType("ANNUITY_EMI")
                        .items(List.of(item1, item12))
                        .build());

        LoanAccount result = loanAccountService.createLoanContract(cmd);

        assertThat(result.getLoanId()).isEqualTo(888L);
        assertThat(result.getLoanAccountNumber()).isEqualTo("LN00000000005555");
        assertThat(result.getLoanStatus()).isEqualTo("APPROVED");
        assertThat(result.getPrincipalAmount()).isEqualByComparingTo("50000000.0000");
        assertThat(result.getRemainingPrincipal()).isEqualByComparingTo("50000000.0000");
        assertThat(result.getAnnualInterestRate()).isEqualByComparingTo("0.0950");
        assertThat(result.getOverdueInterestRate()).isEqualByComparingTo("0.1425");
        assertThat(result.getMaturityDate()).isEqualTo(testDueDate.plusMonths(11));

        verify(loanScheduleRepository).saveAll(any());
    }

    @Test
    @DisplayName("Validation: Rejects when Customer does not exist")
    void testCustomerNotFound() {
        CreateLoanContractCommand cmd = CreateLoanContractCommand.builder()
                .customerId(999L)
                .build();

        when(customerRepository.existsById(999L)).thenReturn(false);

        assertThatThrownBy(() -> loanAccountService.createLoanContract(cmd))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    @Test
    @DisplayName("Validation: Rejects when Linked CASA belongs to a different customer")
    void testLinkedCasaCustomerMismatch() {
        casaAccount.setCustomerId(999L); // different customer

        CreateLoanContractCommand cmd = CreateLoanContractCommand.builder()
                .customerId(100L)
                .linkedCasaAccountId(200L)
                .build();

        when(customerRepository.existsById(100L)).thenReturn(true);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(casaAccount));

        assertThatThrownBy(() -> loanAccountService.createLoanContract(cmd))
                .isInstanceOf(InvalidLoanParameterException.class)
                .hasMessageContaining("does not belong to customer");
    }

    @Test
    @DisplayName("Validation: Rejects when Principal is outside product range")
    void testPrincipalOutOfBounds() {
        CreateLoanContractCommand cmdBelowMin = CreateLoanContractCommand.builder()
                .customerId(100L)
                .linkedCasaAccountId(200L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("1000000.0000")) // < 5,000,000 min
                .termMonths(12)
                .build();

        when(customerRepository.existsById(100L)).thenReturn(true);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(casaAccount));
        when(creditProductCatalogRepository.findByProductCodeAndIsActive("LN_CONSUMER_VND", "Y"))
                .thenReturn(Optional.of(consumerProduct));

        assertThatThrownBy(() -> loanAccountService.createLoanContract(cmdBelowMin))
                .isInstanceOf(InvalidLoanParameterException.class)
                .hasMessageContaining("is below product minimum");
    }

    @Test
    @DisplayName("Validation: Rejects when Term Months is outside product range")
    void testTermMonthsOutOfBounds() {
        CreateLoanContractCommand cmdAboveMax = CreateLoanContractCommand.builder()
                .customerId(100L)
                .linkedCasaAccountId(200L)
                .productCode("LN_CONSUMER_VND")
                .principalAmount(new BigDecimal("50000000.0000"))
                .termMonths(72) // > 60 max
                .build();

        when(customerRepository.existsById(100L)).thenReturn(true);
        when(accountRepository.findById(200L)).thenReturn(Optional.of(casaAccount));
        when(creditProductCatalogRepository.findByProductCodeAndIsActive("LN_CONSUMER_VND", "Y"))
                .thenReturn(Optional.of(consumerProduct));

        assertThatThrownBy(() -> loanAccountService.createLoanContract(cmdAboveMax))
                .isInstanceOf(InvalidLoanParameterException.class)
                .hasMessageContaining("exceeds product maximum");
    }
}

package com.cbs.admin;

import com.cbs.account.domain.Account;
import com.cbs.account.dto.OpenAccountCommand;
import com.cbs.account.service.AccountService;
import com.cbs.admin.domain.BankCalendar;
import com.cbs.admin.domain.SystemOperationalStatus;
import com.cbs.admin.repository.BankCalendarRepository;
import com.cbs.admin.repository.SystemOperationalStatusRepository;
import com.cbs.admin.service.BankCalendarService;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.customer.domain.Customer;
import com.cbs.customer.dto.CreateCustomerCommand;
import com.cbs.customer.service.CustomerService;
import com.cbs.transaction.dto.DepositCommand;
import com.cbs.transaction.dto.TransferCommand;
import com.cbs.transaction.service.TransactionService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class BankCalendarIntegrationTest {

    @Autowired
    private BankCalendarRepository calendarRepository;

    @Autowired
    private BankCalendarService calendarService;

    @Autowired
    private WorkingDateService workingDateService;

    @Autowired
    private SystemOperationalStatusRepository statusRepository;

    @Autowired
    private CustomerService customerService;

    @Autowired
    private AccountService accountService;

    @Autowired
    private TransactionService transactionService;

    private LocalDate originalWorkingDate;
    private String originalCutoff;

    @BeforeEach
    void captureOriginalState() {
        SystemOperationalStatus status = statusRepository.findById(SystemOperationalStatus.SINGLETON_ID).orElseThrow();
        originalWorkingDate = status.getCurrentWorkingDate();
        originalCutoff = status.getIsSystemCutoff();
    }

    @AfterEach
    void restoreOriginalState() {
        SystemOperationalStatus status = statusRepository.findById(SystemOperationalStatus.SINGLETON_ID).orElseThrow();
        status.setCurrentWorkingDate(originalWorkingDate);
        status.setIsSystemCutoff(originalCutoff);
        statusRepository.save(status);
    }

    @Test
    @DisplayName("Bank Calendar: Verifies seeded calendar days and weekend detection")
    void shouldVerifySeededCalendarDays() {
        // Weekday: Wednesday 2026-10-14 -> 'Y'
        LocalDate wednesday = LocalDate.of(2026, 10, 14);
        assertThat(calendarService.isWorkingDay(wednesday)).isTrue();

        // Weekend: Saturday 2026-10-17 -> 'N'
        LocalDate saturday = LocalDate.of(2026, 10, 17);
        assertThat(calendarService.isWorkingDay(saturday)).isFalse();

        // Weekend: Sunday 2026-10-18 -> 'N'
        LocalDate sunday = LocalDate.of(2026, 10, 18);
        assertThat(calendarService.isWorkingDay(sunday)).isFalse();

        // Weekday: Monday 2026-10-19 -> 'Y'
        LocalDate monday = LocalDate.of(2026, 10, 19);
        assertThat(calendarService.isWorkingDay(monday)).isTrue();
    }

    @Test
    @DisplayName("Bank Calendar: findNextWorkingDay automatically skips weekends and declared holidays")
    void shouldSkipWeekendsAndHolidays() {
        LocalDate friday = LocalDate.of(2026, 10, 16);

        // From Friday 2026-10-16, the next working day should skip Sat (17) and Sun (18) to reach Mon (19)
        LocalDate nextWorkingDay = calendarService.getNextWorkingDay(friday);
        assertThat(nextWorkingDay).isEqualTo(LocalDate.of(2026, 10, 19));

        // Now configure Monday 2026-10-19 as a holiday
        calendarService.configureDay(nextWorkingDay, false);
        try {
            LocalDate nextAfterHoliday = calendarService.getNextWorkingDay(friday);
            // Skips Sat (17), Sun (18), and Mon (19) -> reaches Tue (20)
            assertThat(nextAfterHoliday).isEqualTo(LocalDate.of(2026, 10, 20));
        } finally {
            // Restore Monday as working day
            calendarService.configureDay(nextWorkingDay, true);
        }
    }

    @Test
    @DisplayName("Cutoff Guard: Online transfer is blocked during system cutoff, then succeeds once cutoff is released")
    void shouldEnforceCutoffGuardOnDaytimeTransactions() {
        // Create accounts and fund
        Customer custA = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Cutoff Test A")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Customer custB = customerService.createCustomer(CreateCustomerCommand.builder()
                .customerType("INDIVIDUAL")
                .fullName("Cutoff Test B")
                .nationalId("CCCD_" + UUID.randomUUID().toString().substring(0, 10))
                .kycStatus("VERIFIED")
                .status("ACTIVE")
                .build());

        Account accA = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custA.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        Account accB = accountService.openAccount(OpenAccountCommand.builder()
                .customerId(custB.getCustomerId())
                .productCode("CASA_VND")
                .branchCode("VN001")
                .status("ACTIVE")
                .build());

        // Deposit 1,000,000 VND
        transactionService.deposit(DepositCommand.builder()
                .channelId("BR")
                .idempotencyKey("dep-" + UUID.randomUUID())
                .accountNumber(accA.getAccountNumber())
                .amount(new BigDecimal("1000000.0000"))
                .branchCode("VN001")
                .makerId("teller")
                .build());

        // 1. Enable Cutoff
        workingDateService.enableCutoff();
        assertThat(workingDateService.isSystemCutoff()).isTrue();

        // 2. Attempt transfer during Cutoff -> Must fail immediately
        TransferCommand trfCmd = TransferCommand.builder()
                .channelId("MB")
                .idempotencyKey("trf-" + UUID.randomUUID())
                .fromAccountNumber(accA.getAccountNumber())
                .toAccountNumber(accB.getAccountNumber())
                .amount(new BigDecimal("200000.0000"))
                .makerId("user")
                .build();

        assertThatThrownBy(() -> transactionService.transfer(trfCmd))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Cutoff");

        // 3. Disable Cutoff -> Transfer now succeeds
        workingDateService.disableCutoff();
        assertThat(workingDateService.isSystemCutoff()).isFalse();

        var receipt = transactionService.transfer(trfCmd);
        assertThat(receipt.status()).isEqualTo("COMPLETED");
    }

    @Test
    @DisplayName("Rollover: Rollover advances working date to next business day and reopens cutoff")
    void shouldExecuteWorkingDateRollover() {
        workingDateService.enableCutoff();
        assertThat(workingDateService.isSystemCutoff()).isTrue();

        LocalDate current = workingDateService.getCurrentWorkingDate();
        LocalDate expectedNext = calendarService.getNextWorkingDay(current);

        LocalDate rolled = workingDateService.rolloverWorkingDate();

        assertThat(rolled).isEqualTo(expectedNext);
        assertThat(workingDateService.getCurrentWorkingDate()).isEqualTo(expectedNext);
        assertThat(workingDateService.isSystemCutoff()).isFalse();
    }
}

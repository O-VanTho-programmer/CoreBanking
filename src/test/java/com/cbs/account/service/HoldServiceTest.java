package com.cbs.account.service;

import com.cbs.account.domain.Account;
import com.cbs.account.domain.AccountHold;
import com.cbs.account.dto.PlaceHoldCommand;
import com.cbs.account.repository.AccountHoldRepository;
import com.cbs.account.repository.AccountRepository;
import com.cbs.admin.service.WorkingDateService;
import com.cbs.common.codelist.service.CodeListService;
import com.cbs.common.exception.InsufficientFundsException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HoldServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private AccountHoldRepository holdRepository;

    @Mock
    private WorkingDateService workingDateService;

    @Mock
    private CodeListService codeListService;

    @InjectMocks
    private HoldService holdService;

    private final LocalDate workingDate = LocalDate.of(2026, 10, 21);

    @Test
    @DisplayName("placeHold(): successfully places hold when Available >= holdAmount")
    void shouldPlaceHoldWhenFundsAvailable() {
        Account account = Account.builder()
                .accountId(100L)
                .ledgerBalance(new BigDecimal("1000000.0000"))
                .holdBalance(BigDecimal.ZERO.setScale(4))
                .status("ACTIVE")
                .build();

        PlaceHoldCommand command = PlaceHoldCommand.builder()
                .accountId(100L)
                .amount(new BigDecimal("300000.0000"))
                .reasonCode("CARD_AUTH")
                .referenceId("AUTH-1234")
                .build();

        when(codeListService.isValid("HOLD_REASON", "CARD_AUTH")).thenReturn(true);
        when(accountRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(account));
        when(workingDateService.getCurrentWorkingDate()).thenReturn(workingDate);
        when(holdRepository.save(any(AccountHold.class))).thenAnswer(inv -> {
            AccountHold h = inv.getArgument(0);
            h.setHoldId(501L);
            return h;
        });

        AccountHold result = holdService.placeHold(command);

        assertThat(result.getHoldId()).isEqualTo(501L);
        assertThat(result.getStatus()).isEqualTo("ACTIVE");
        assertThat(result.getReleasedAt()).isNull();
        assertThat(account.getHoldBalance()).isEqualByComparingTo("300000.0000");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("700000.0000");
    }

    @Test
    @DisplayName("placeHold(): throws InsufficientFundsException when Available < holdAmount")
    void shouldRejectHoldWhenInsufficientFunds() {
        Account account = Account.builder()
                .accountId(100L)
                .ledgerBalance(new BigDecimal("500000.0000"))
                .holdBalance(new BigDecimal("300000.0000")) // available = 200,000
                .status("ACTIVE")
                .build();

        PlaceHoldCommand command = PlaceHoldCommand.builder()
                .accountId(100L)
                .amount(new BigDecimal("250000.0000")) // requires 250,000 > 200,000
                .reasonCode("CARD_AUTH")
                .build();

        when(codeListService.isValid("HOLD_REASON", "CARD_AUTH")).thenReturn(true);
        when(accountRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(account));

        assertThatThrownBy(() -> holdService.placeHold(command))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("AvailableBalance=200000.0000");
    }

    @Test
    @DisplayName("releaseHold(): releases active hold and restores available balance")
    void shouldReleaseHoldSuccessfully() {
        Account account = Account.builder()
                .accountId(100L)
                .ledgerBalance(new BigDecimal("1000000.0000"))
                .holdBalance(new BigDecimal("300000.0000"))
                .build();

        AccountHold hold = AccountHold.builder()
                .holdId(501L)
                .accountId(100L)
                .holdAmount(new BigDecimal("300000.0000"))
                .status("ACTIVE")
                .build();

        when(holdRepository.findById(501L)).thenReturn(Optional.of(hold));
        when(accountRepository.findByIdForUpdate(100L)).thenReturn(Optional.of(account));
        when(holdRepository.save(any(AccountHold.class))).thenAnswer(inv -> inv.getArgument(0));

        AccountHold released = holdService.releaseHold(501L);

        assertThat(released.getStatus()).isEqualTo("RELEASED");
        assertThat(released.getReleasedAt()).isNotNull();
        assertThat(account.getHoldBalance()).isEqualByComparingTo("0.0000");
        assertThat(account.getAvailableBalance()).isEqualByComparingTo("1000000.0000");
    }
}

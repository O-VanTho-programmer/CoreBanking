package com.cbs.accounting.service;

import com.cbs.accounting.domain.AccountingRuleDefinition;
import com.cbs.accounting.dto.JournalLeg;
import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.repository.AccountingRuleRepository;
import com.cbs.common.exception.AccountingRuleNotFoundException;
import com.cbs.common.exception.UnbalancedTransactionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostingEngineServiceTest {

    @Mock
    private AccountingRuleRepository ruleRepository;

    @Mock
    private AccountResolverService accountResolverService;

    @InjectMocks
    private PostingEngineService postingEngineService;

    private final LocalDate workingDate = LocalDate.of(2026, 10, 20);

    @Test
    @DisplayName("CASH_DEP: generates 2 balanced legs (D: 1011 SYS_CASH_VAULT, C: 4211 Customer Account)")
    void shouldGenerateBalancedDepositLegs() {
        AccountingRuleDefinition leg1 = AccountingRuleDefinition.builder()
                .ruleId(101L).trxCode("CASH_DEP").entryGroup(1).entryIndex(1)
                .accountRole("SYSTEM_GL").abstractAcctName("SYS_CASH_VAULT")
                .debitOrCredit("D").amountSource("TRX_AMOUNT").requiresFee("N")
                .narrationTemplate("Cash deposit").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg2 = AccountingRuleDefinition.builder()
                .ruleId(102L).trxCode("CASH_DEP").entryGroup(1).entryIndex(2)
                .accountRole("CUSTOMER_ACCOUNT")
                .debitOrCredit("C").amountSource("TRX_AMOUNT").requiresFee("N")
                .narrationTemplate("Cash deposit").effectiveFrom(workingDate).build();

        when(ruleRepository.findActiveRulesByTrxCode("CASH_DEP", workingDate))
                .thenReturn(List.of(leg1, leg2));
        when(accountResolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND"))
                .thenReturn("1011");

        PostingRequest request = PostingRequest.builder()
                .trxCode("CASH_DEP")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("1000000.0000"))
                .customerAccountId(888L)
                .customerGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.totalDebit()).isEqualByComparingTo("1000000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("1000000.0000");
        assertThat(result.legs()).hasSize(2);

        JournalLeg debitLeg = result.legs().get(0);
        assertThat(debitLeg.glCode()).isEqualTo("1011");
        assertThat(debitLeg.drCr()).isEqualTo("D");
        assertThat(debitLeg.accountId()).isNull();
        assertThat(debitLeg.amount()).isEqualByComparingTo("1000000.0000");

        JournalLeg creditLeg = result.legs().get(1);
        assertThat(creditLeg.glCode()).isEqualTo("4211");
        assertThat(creditLeg.drCr()).isEqualTo("C");
        assertThat(creditLeg.accountId()).isEqualTo(888L);
        assertThat(creditLeg.amount()).isEqualByComparingTo("1000000.0000");
    }

    @Test
    @DisplayName("CASH_WDR: generates 2 balanced legs (D: 4211 Customer Account, C: 1011 SYS_CASH_VAULT)")
    void shouldGenerateBalancedWithdrawalLegs() {
        AccountingRuleDefinition leg1 = AccountingRuleDefinition.builder()
                .ruleId(201L).trxCode("CASH_WDR").entryGroup(1).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT")
                .debitOrCredit("D").amountSource("TRX_AMOUNT").requiresFee("N")
                .narrationTemplate("Cash withdrawal").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg2 = AccountingRuleDefinition.builder()
                .ruleId(202L).trxCode("CASH_WDR").entryGroup(1).entryIndex(2)
                .accountRole("SYSTEM_GL").abstractAcctName("SYS_CASH_VAULT")
                .debitOrCredit("C").amountSource("TRX_AMOUNT").requiresFee("N")
                .narrationTemplate("Cash withdrawal").effectiveFrom(workingDate).build();

        when(ruleRepository.findActiveRulesByTrxCode("CASH_WDR", workingDate))
                .thenReturn(List.of(leg1, leg2));
        when(accountResolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND"))
                .thenReturn("1011");

        PostingRequest request = PostingRequest.builder()
                .trxCode("CASH_WDR")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("500000.0000"))
                .customerAccountId(888L)
                .customerGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.totalDebit()).isEqualByComparingTo("500000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("500000.0000");
        assertThat(result.legs()).hasSize(2);
        assertThat(result.legs().get(0).drCr()).isEqualTo("D");
        assertThat(result.legs().get(1).drCr()).isEqualTo("C");
    }

    @Test
    @DisplayName("TRF_INTERNAL with fee: generates 4 legs balanced across group 1 and group 2")
    void shouldGenerateTransferWithFeeLegs() {
        AccountingRuleDefinition leg1 = AccountingRuleDefinition.builder()
                .ruleId(301L).trxCode("TRF_INTERNAL").entryGroup(1).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("TRX_AMOUNT")
                .requiresFee("N").narrationTemplate("Transfer out").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg2 = AccountingRuleDefinition.builder()
                .ruleId(302L).trxCode("TRF_INTERNAL").entryGroup(1).entryIndex(2)
                .accountRole("TARGET_ACCOUNT").debitOrCredit("C").amountSource("TRX_AMOUNT")
                .requiresFee("N").narrationTemplate("Transfer in").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg3 = AccountingRuleDefinition.builder()
                .ruleId(303L).trxCode("TRF_INTERNAL").entryGroup(2).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("FEE_AMOUNT")
                .requiresFee("Y").narrationTemplate("Transfer fee").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg4 = AccountingRuleDefinition.builder()
                .ruleId(304L).trxCode("TRF_INTERNAL").entryGroup(2).entryIndex(2)
                .accountRole("SYSTEM_GL").abstractAcctName("SYS_FEE_INCOME")
                .debitOrCredit("C").amountSource("FEE_AMOUNT")
                .requiresFee("Y").narrationTemplate("Transfer fee").effectiveFrom(workingDate).build();

        when(ruleRepository.findActiveRulesByTrxCode("TRF_INTERNAL", workingDate))
                .thenReturn(List.of(leg1, leg2, leg3, leg4));
        when(accountResolverService.resolvePhysicalGl("SYS_FEE_INCOME", "VN001", "VND", "CASA_VND"))
                .thenReturn("7111");

        PostingRequest request = PostingRequest.builder()
                .trxCode("TRF_INTERNAL")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("2000000.0000"))
                .feeAmount(new BigDecimal("11000.0000"))
                .customerAccountId(100L)
                .customerGlCode("4211")
                .targetAccountId(200L)
                .targetGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.totalDebit()).isEqualByComparingTo("2011000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("2011000.0000");
        assertThat(result.legs()).hasSize(4);
    }

    @Test
    @DisplayName("TRF_INTERNAL with zero fee: cleanly skips fee legs and produces 2 balanced principal legs")
    void shouldSkipFeeLegsWhenFeeIsZero() {
        AccountingRuleDefinition leg1 = AccountingRuleDefinition.builder()
                .ruleId(301L).trxCode("TRF_INTERNAL").entryGroup(1).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("TRX_AMOUNT")
                .requiresFee("N").narrationTemplate("Transfer out").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg2 = AccountingRuleDefinition.builder()
                .ruleId(302L).trxCode("TRF_INTERNAL").entryGroup(1).entryIndex(2)
                .accountRole("TARGET_ACCOUNT").debitOrCredit("C").amountSource("TRX_AMOUNT")
                .requiresFee("N").narrationTemplate("Transfer in").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg3 = AccountingRuleDefinition.builder()
                .ruleId(303L).trxCode("TRF_INTERNAL").entryGroup(2).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("FEE_AMOUNT")
                .requiresFee("Y").narrationTemplate("Transfer fee").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg4 = AccountingRuleDefinition.builder()
                .ruleId(304L).trxCode("TRF_INTERNAL").entryGroup(2).entryIndex(2)
                .accountRole("SYSTEM_GL").abstractAcctName("SYS_FEE_INCOME")
                .debitOrCredit("C").amountSource("FEE_AMOUNT")
                .requiresFee("Y").narrationTemplate("Transfer fee").effectiveFrom(workingDate).build();

        when(ruleRepository.findActiveRulesByTrxCode("TRF_INTERNAL", workingDate))
                .thenReturn(List.of(leg1, leg2, leg3, leg4));

        PostingRequest request = PostingRequest.builder()
                .trxCode("TRF_INTERNAL")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("2000000.0000"))
                .feeAmount(BigDecimal.ZERO) // zero fee!
                .customerAccountId(100L)
                .customerGlCode("4211")
                .targetAccountId(200L)
                .targetGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.totalDebit()).isEqualByComparingTo("2000000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("2000000.0000");
        assertThat(result.legs()).hasSize(2); // Only principal legs generated!
    }

    @Test
    @DisplayName("Throws UnbalancedTransactionException when rule matrix definition is asymmetric")
    void shouldThrowUnbalancedExceptionWhenRulesAreAsymmetric() {
        AccountingRuleDefinition leg1 = AccountingRuleDefinition.builder()
                .ruleId(101L).trxCode("CORRUPTED").entryGroup(1).entryIndex(1)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("TRX_AMOUNT")
                .requiresFee("N").narrationTemplate("Corrupted D").effectiveFrom(workingDate).build();

        AccountingRuleDefinition leg2 = AccountingRuleDefinition.builder()
                .ruleId(102L).trxCode("CORRUPTED").entryGroup(1).entryIndex(2)
                .accountRole("CUSTOMER_ACCOUNT").debitOrCredit("D").amountSource("TRX_AMOUNT") // Asymmetric: 2 debits!
                .requiresFee("N").narrationTemplate("Corrupted D2").effectiveFrom(workingDate).build();

        when(ruleRepository.findActiveRulesByTrxCode("CORRUPTED", workingDate))
                .thenReturn(List.of(leg1, leg2));

        PostingRequest request = PostingRequest.builder()
                .trxCode("CORRUPTED")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("100000.0000"))
                .customerAccountId(100L)
                .customerGlCode("4211")
                .build();

        assertThatThrownBy(() -> postingEngineService.generateAndValidateEntries(request))
                .isInstanceOf(UnbalancedTransactionException.class)
                .hasMessageContaining("CORRUPTED");
    }

    @Test
    @DisplayName("Throws AccountingRuleNotFoundException when no active rules exist for trxCode")
    void shouldThrowNotFoundExceptionForUnknownTrxCode() {
        when(ruleRepository.findActiveRulesByTrxCode("UNKNOWN_TRX", workingDate))
                .thenReturn(Collections.emptyList());

        PostingRequest request = PostingRequest.builder()
                .trxCode("UNKNOWN_TRX")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("100000.0000"))
                .build();

        assertThatThrownBy(() -> postingEngineService.generateAndValidateEntries(request))
                .isInstanceOf(AccountingRuleNotFoundException.class)
                .hasMessageContaining("UNKNOWN_TRX");
    }
}

package com.cbs.accounting;

import com.cbs.accounting.dto.PostingRequest;
import com.cbs.accounting.dto.PostingResult;
import com.cbs.accounting.service.PostingEngineService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
class PostingEngineIntegrationTest {

    @Autowired
    private PostingEngineService postingEngineService;

    // Operational working date matching Flyway V2 seed
    private final LocalDate workingDate = LocalDate.of(2026, 10, 13);

    @Test
    @DisplayName("CASH_DEP: evaluates seeded V2 rule and resolves GL 1011 (Vault) and GL 4211 (CASA)")
    void shouldEvaluateSeededCashDepositRule() {
        PostingRequest request = PostingRequest.builder()
                .trxCode("CASH_DEP")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("1000000.0000"))
                .customerAccountId(10001L)
                .customerGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.trxCode()).isEqualTo("CASH_DEP");
        assertThat(result.totalDebit()).isEqualByComparingTo("1000000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("1000000.0000");
        assertThat(result.legs()).hasSize(2);

        // Leg 1: D: 1011 (SYS_CASH_VAULT)
        assertThat(result.legs().get(0).drCr()).isEqualTo("D");
        assertThat(result.legs().get(0).glCode()).isEqualTo("1011");
        assertThat(result.legs().get(0).accountId()).isNull();

        // Leg 2: C: 4211 (Customer CASA)
        assertThat(result.legs().get(1).drCr()).isEqualTo("C");
        assertThat(result.legs().get(1).glCode()).isEqualTo("4211");
        assertThat(result.legs().get(1).accountId()).isEqualTo(10001L);
    }

    @Test
    @DisplayName("CASH_WDR: evaluates seeded V2 rule and balances across D 4211 and C 1011")
    void shouldEvaluateSeededCashWithdrawalRule() {
        PostingRequest request = PostingRequest.builder()
                .trxCode("CASH_WDR")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("500000.0000"))
                .customerAccountId(10001L)
                .customerGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.trxCode()).isEqualTo("CASH_WDR");
        assertThat(result.totalDebit()).isEqualByComparingTo("500000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("500000.0000");
        assertThat(result.legs()).hasSize(2);

        // Leg 1: D: 4211 (Customer CASA)
        assertThat(result.legs().get(0).drCr()).isEqualTo("D");
        assertThat(result.legs().get(0).glCode()).isEqualTo("4211");

        // Leg 2: C: 1011 (SYS_CASH_VAULT)
        assertThat(result.legs().get(1).drCr()).isEqualTo("C");
        assertThat(result.legs().get(1).glCode()).isEqualTo("1011");
    }

    @Test
    @DisplayName("TRF_INTERNAL with fee: evaluates seeded 4 legs (Group 1 Principal, Group 2 Fee)")
    void shouldEvaluateSeededTransferRuleWithFee() {
        PostingRequest request = PostingRequest.builder()
                .trxCode("TRF_INTERNAL")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("2000000.0000"))
                .feeAmount(new BigDecimal("11000.0000"))
                .customerAccountId(10001L)
                .customerGlCode("4211")
                .targetAccountId(10002L)
                .targetGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.trxCode()).isEqualTo("TRF_INTERNAL");
        assertThat(result.totalDebit()).isEqualByComparingTo("2011000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("2011000.0000");
        assertThat(result.legs()).hasSize(4);

        // Group 1: Leg 1 D 4211 (Customer), Leg 2 C 4211 (Target)
        assertThat(result.legs().get(0).entryGroup()).isEqualTo(1);
        assertThat(result.legs().get(0).drCr()).isEqualTo("D");
        assertThat(result.legs().get(0).accountId()).isEqualTo(10001L);

        assertThat(result.legs().get(1).entryGroup()).isEqualTo(1);
        assertThat(result.legs().get(1).drCr()).isEqualTo("C");
        assertThat(result.legs().get(1).accountId()).isEqualTo(10002L);

        // Group 2: Leg 3 D 4211 (Customer), Leg 4 C 7111 (SYS_FEE_INCOME)
        assertThat(result.legs().get(2).entryGroup()).isEqualTo(2);
        assertThat(result.legs().get(2).drCr()).isEqualTo("D");
        assertThat(result.legs().get(2).amount()).isEqualByComparingTo("11000.0000");

        assertThat(result.legs().get(3).entryGroup()).isEqualTo(2);
        assertThat(result.legs().get(3).drCr()).isEqualTo("C");
        assertThat(result.legs().get(3).glCode()).isEqualTo("7111");
        assertThat(result.legs().get(3).amount()).isEqualByComparingTo("11000.0000");
    }

    @Test
    @DisplayName("TRF_INTERNAL with zero fee: cleanly skips Group 2 fee legs and balances Group 1")
    void shouldEvaluateSeededTransferRuleWithZeroFee() {
        PostingRequest request = PostingRequest.builder()
                .trxCode("TRF_INTERNAL")
                .currency("VND")
                .workingDate(workingDate)
                .trxAmount(new BigDecimal("2000000.0000"))
                .feeAmount(BigDecimal.ZERO)
                .customerAccountId(10001L)
                .customerGlCode("4211")
                .targetAccountId(10002L)
                .targetGlCode("4211")
                .branchCode("VN001")
                .productCode("CASA_VND")
                .build();

        PostingResult result = postingEngineService.generateAndValidateEntries(request);

        assertThat(result.trxCode()).isEqualTo("TRF_INTERNAL");
        assertThat(result.totalDebit()).isEqualByComparingTo("2000000.0000");
        assertThat(result.totalCredit()).isEqualByComparingTo("2000000.0000");
        assertThat(result.legs()).hasSize(2);
        assertThat(result.legs().get(0).entryGroup()).isEqualTo(1);
        assertThat(result.legs().get(1).entryGroup()).isEqualTo(1);
    }
}

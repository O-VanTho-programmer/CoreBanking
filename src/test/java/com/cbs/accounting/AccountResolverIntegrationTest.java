package com.cbs.accounting;

import com.cbs.accounting.service.AccountResolverService;
import com.cbs.common.exception.AccountMappingNotFoundException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@SpringBootTest
class AccountResolverIntegrationTest {

    @Autowired
    private AccountResolverService resolverService;

    @Test
    @DisplayName("Resolves SYS_CASH_VAULT -> GL 1011 using wildcard branch and product fallback")
    void shouldResolveCashVaultWithFallback() {
        // Seed in V2 has (SYS_CASH_VAULT, branch='ALL', currency='VND', product='ALL') -> '1011'
        String glCode = resolverService.resolvePhysicalGl("SYS_CASH_VAULT", "VN001", "VND", "CASA_VND");
        assertThat(glCode).isEqualTo("1011");
    }

    @Test
    @DisplayName("Resolves SYS_FEE_INCOME -> GL 7111")
    void shouldResolveFeeIncome() {
        // Seed in V2 has (SYS_FEE_INCOME, branch='ALL', currency='VND', product='ALL') -> '7111'
        String glCode = resolverService.resolvePhysicalGl("SYS_FEE_INCOME", "VND");
        assertThat(glCode).isEqualTo("7111");
    }

    @Test
    @DisplayName("Resolves SYS_INT_EXPENSE -> GL 8011 and SYS_INT_PAYABLE -> GL 4911")
    void shouldResolveInterestMappings() {
        String expenseGl = resolverService.resolvePhysicalGl("SYS_INT_EXPENSE", "VND");
        assertThat(expenseGl).isEqualTo("8011");

        String payableGl = resolverService.resolvePhysicalGl("SYS_INT_PAYABLE", "VND");
        assertThat(payableGl).isEqualTo("4911");
    }

    @Test
    @DisplayName("Throws AccountMappingNotFoundException when mapping role does not exist")
    void shouldThrowExceptionForUnknownMapping() {
        assertThatThrownBy(() -> resolverService.resolvePhysicalGl("UNKNOWN_ROLE", "VND"))
                .isInstanceOf(AccountMappingNotFoundException.class)
                .hasMessageContaining("UNKNOWN_ROLE");
    }
}

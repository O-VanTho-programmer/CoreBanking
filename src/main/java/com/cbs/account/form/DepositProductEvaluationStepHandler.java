package com.cbs.account.form;

import com.cbs.account.domain.DptProductCatalog;
import com.cbs.account.repository.DptProductCatalogRepository;
import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
@RequiredArgsConstructor
@Slf4j
public class DepositProductEvaluationStepHandler implements FormStepHandler {

    private final DptProductCatalogRepository dptProductCatalogRepository;

    @Override
    public String getStepCode() {
        return "EVALUATE_DEPOSIT_PRODUCT";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        String productCode = context.getString("productCode");
        if (productCode == null || productCode.isBlank()) {
            productCode = "CASA_VND";
        }

        Optional<DptProductCatalog> productOpt = dptProductCatalogRepository.findByProductCodeAndIsActive(productCode, "Y");
        if (productOpt.isEmpty()) {
            context.putError("productCode", "Product '" + productCode + "' not found or inactive");
            return;
        }

        DptProductCatalog product = productOpt.get();
        String productType = (product.getProductType() != null) ? product.getProductType() : "CASA";
        BigDecimal baseRate = (product.getAnnualRate() != null) ? product.getAnnualRate() : BigDecimal.ZERO;

        context.putUpdate("productType", productType);
        context.putUpdate("currency", product.getCurrency());
        context.putUpdate("baseInterestRate", baseRate);

        if ("CASA".equalsIgnoreCase(productType)) {
            // For CASA: FD-specific fields are visible but disabled
            context.putDisabled("initialDeposit");
            context.putDisabled("termMonths");
            context.putDisabled("rolloverOption");
            context.putDisabled("linkedCasaAccountNumber");
            context.putDisabled("requestInterestMargin");
            context.putDisabled("marginReason");
            context.putDisabled("interestMargin");

            context.putUpdate("initialDeposit", BigDecimal.ZERO);
            context.putUpdate("requestInterestMargin", false);
            context.putUpdate("marginReason", null);
            context.putUpdate("interestMargin", BigDecimal.ZERO);
            context.putUpdate("effectiveRate", baseRate);
        } else {
            // For FD: FD-specific fields are enabled
            Object reqMarginObj = context.getFormData().get("requestInterestMargin");
            boolean requestMargin = Boolean.TRUE.equals(reqMarginObj) || "true".equalsIgnoreCase(String.valueOf(reqMarginObj));
            String marginReason = context.getString("marginReason");

            if (!requestMargin) {
                // Not requested: disable both marginReason and interestMargin
                context.putDisabled("marginReason");
                context.putDisabled("interestMargin");
                context.putUpdate("interestMargin", BigDecimal.ZERO);
                context.putUpdate("marginReason", null);
                context.putUpdate("effectiveRate", baseRate);
            } else {
                // Requested: marginReason is enabled.
                // interestMargin is enabled ONLY if marginReason has been selected.
                if (marginReason == null || marginReason.isBlank()) {
                    context.putDisabled("interestMargin");
                    context.putUpdate("interestMargin", BigDecimal.ZERO);
                    context.putUpdate("effectiveRate", baseRate);
                } else {
                    // Both requestMargin == true and marginReason is selected: unlock interestMargin!
                    BigDecimal margin = context.getBigDecimal("interestMargin");
                    if (margin == null) {
                        margin = BigDecimal.ZERO;
                    }
                    BigDecimal maxMargin = (product.getMaxInterestMargin() != null) ? product.getMaxInterestMargin() : new BigDecimal("0.0200");
                    if (margin.compareTo(BigDecimal.ZERO) < 0) {
                        context.putError("interestMargin", "Interest margin cannot be negative");
                    } else if (margin.compareTo(maxMargin) > 0) {
                        context.putError("interestMargin", String.format("Interest margin %s exceeds maximum allowed %s", margin, maxMargin));
                    }
                    context.putUpdate("effectiveRate", baseRate.add(margin));
                }
            }

            // Validate minimum opening placement amount
            BigDecimal initialDeposit = context.getBigDecimal("initialDeposit");
            if (initialDeposit != null && product.getMinOpenAmount() != null) {
                if (initialDeposit.compareTo(product.getMinOpenAmount()) < 0) {
                    context.putError("initialDeposit", String.format(
                            "Minimum initial placement for %s is %s %s",
                            productCode, product.getMinOpenAmount(), product.getCurrency()));
                }
            }

            // Rollover option validation for settlement CASA account
            String rolloverOption = context.getString("rolloverOption");
            if ("PRINCIPAL_AND_INTEREST".equalsIgnoreCase(rolloverOption)) {
                context.putDisabled("linkedCasaAccountNumber");
            }
        }
    }
}

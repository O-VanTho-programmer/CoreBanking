package com.cbs.lending.form;

import com.cbs.common.form.FormEvaluationContext;
import com.cbs.common.form.FormStepHandler;
import com.cbs.lending.domain.CreditProductCatalog;
import com.cbs.lending.repository.CreditProductCatalogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class LoanEmiPreviewStepHandler implements FormStepHandler {

    private final CreditProductCatalogRepository creditProductCatalogRepository;

    @Override
    public String getStepCode() {
        return "PREVIEW_LOAN_EMI";
    }

    @Override
    public void execute(FormEvaluationContext context) {
        BigDecimal principal = context.getBigDecimal("principalAmount");
        Integer months = context.getInteger("termMonths");
        BigDecimal margin = context.getBigDecimal("interestMargin");
        String productCode = context.getString("productCode");

        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0 || months == null || months <= 0) {
            context.putUpdate("monthlyEstimatedEmi", BigDecimal.ZERO);
            return;
        }

        BigDecimal baseRate = new BigDecimal("0.0950");
        if (productCode != null && !productCode.isBlank()) {
            Optional<CreditProductCatalog> catalogOpt = creditProductCatalogRepository.findByProductCodeAndIsActive(productCode, "Y");
            if (catalogOpt.isPresent()) {
                baseRate = catalogOpt.get().getBaseInterestRate();
                if (margin != null) {
                    if (margin.compareTo(catalogOpt.get().getMinMarginRate()) < 0 ||
                        margin.compareTo(catalogOpt.get().getMaxMarginRate()) > 0) {
                        context.putError("interestMargin", String.format(
                                "Margin rate %s is outside allowed range [%s, %s]!",
                                margin, catalogOpt.get().getMinMarginRate(), catalogOpt.get().getMaxMarginRate()));
                    }
                }
            }
        }

        BigDecimal effectiveAnnualRate = baseRate.add(margin != null ? margin : new BigDecimal("0.0250"));
        BigDecimal monthlyRate = effectiveAnnualRate.divide(BigDecimal.valueOf(12), 10, RoundingMode.HALF_UP);

        // EMI = P * r * (1+r)^n / ((1+r)^n - 1)
        double r = monthlyRate.doubleValue();
        double p = principal.doubleValue();
        int n = months;

        double factor = Math.pow(1 + r, n);
        double emi = (p * r * factor) / (factor - 1);

        context.putUpdate("monthlyEstimatedEmi", new BigDecimal(Math.round(emi)));
    }
}

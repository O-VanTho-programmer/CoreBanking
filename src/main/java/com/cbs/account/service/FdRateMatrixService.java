package com.cbs.account.service;

import com.cbs.account.domain.FdInterestRateMatrix;
import com.cbs.account.dto.TermRateOptionDto;
import com.cbs.account.repository.FdInterestRateMatrixRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class FdRateMatrixService {

    private final FdInterestRateMatrixRepository fdInterestRateMatrixRepository;

    /**
     * Retrieves all active deposit terms and their annual base rates for a product on a working date.
     */
    public List<TermRateOptionDto> getAvailableTerms(String productCode, LocalDate workingDate) {
        if (productCode == null || productCode.isBlank()) {
            return List.of();
        }

        List<FdInterestRateMatrix> matrices = fdInterestRateMatrixRepository.findAllActiveTermsForProduct(
                productCode.trim(), workingDate);

        return matrices.stream()
                .map(m -> {
                    BigDecimal percentage = m.getBaseRate().multiply(new BigDecimal("100")).setScale(2, RoundingMode.HALF_UP);
                    String label = String.format("%d Month%s (%s%%/yr)",
                            m.getTermMonths(),
                            m.getTermMonths() > 1 ? "s" : "",
                            percentage.toPlainString());
                    return new TermRateOptionDto(m.getTermMonths(), m.getBaseRate(), label);
                })
                .toList();
    }

    /**
     * Resolves the active base interest rate for a given product and deposit term.
     */
    public Optional<BigDecimal> resolveBaseRate(String productCode, Integer termMonths, LocalDate workingDate) {
        if (productCode == null || termMonths == null) {
            return Optional.empty();
        }
        return fdInterestRateMatrixRepository.findActiveRate(productCode.trim(), termMonths, workingDate)
                .map(FdInterestRateMatrix::getBaseRate);
    }
}

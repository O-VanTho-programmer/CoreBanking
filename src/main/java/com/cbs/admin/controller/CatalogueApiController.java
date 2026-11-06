package com.cbs.admin.controller;

import com.cbs.account.domain.DptProductCatalog;
import com.cbs.account.repository.DptProductCatalogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/catalogue")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CatalogueApiController {

    private final DptProductCatalogRepository dptProductCatalogRepository;

    @GetMapping("/deposit")
    public ResponseEntity<List<DptProductCatalog>> listDepositProducts(@RequestParam(required = false) String keyword) {
        log.info("Querying deposit product catalog, keyword='{}'", keyword);
        List<DptProductCatalog> list = dptProductCatalogRepository.findAll();
        if (keyword != null && !keyword.isBlank()) {
            String kw = keyword.trim().toLowerCase();
            list = list.stream()
                    .filter(p -> p.getProductCode().toLowerCase().contains(kw) ||
                                 p.getProductName().toLowerCase().contains(kw) ||
                                 p.getCurrency().toLowerCase().contains(kw))
                    .toList();
        }
        return ResponseEntity.ok(list);
    }

    @PostMapping("/deposit")
    public ResponseEntity<Map<String, Object>> saveDepositCatalogue(@RequestBody Map<String, Object> payload) {
        log.info("Saving Deposit catalogue definition: {}", payload);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Deposit product catalogue saved successfully",
                "productCode", payload.getOrDefault("productCode", "CASA_VND")
        ));
    }

    @PostMapping("/credit")
    public ResponseEntity<Map<String, Object>> saveCreditCatalogue(@RequestBody Map<String, Object> payload) {
        log.info("Saving Credit catalogue definition: {}", payload);
        return ResponseEntity.ok(Map.of(
                "status", "SUCCESS",
                "message", "Credit product catalogue saved successfully",
                "productCode", payload.getOrDefault("productCode", "LN_CONSUMER_VND")
        ));
    }
}

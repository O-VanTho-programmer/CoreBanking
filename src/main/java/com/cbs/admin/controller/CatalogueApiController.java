package com.cbs.admin.controller;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/catalogue")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class CatalogueApiController {

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

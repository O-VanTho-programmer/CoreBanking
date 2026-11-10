package com.cbs.admin.controller;

import com.cbs.common.codelist.service.CodeListService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.OffsetDateTime;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@Slf4j
@CrossOrigin(origins = "*")
public class AdminApiController {

    private final StringRedisTemplate stringRedisTemplate;
    private final CodeListService codeListService;

    @PostMapping("/cache/clear")
    public ResponseEntity<Map<String, Object>> clearCache() {
        log.info("Admin request: flushing Redis cache and refreshing CodeLists...");
        try {
            stringRedisTemplate.execute((RedisConnection connection) -> {
                connection.serverCommands().flushDb();
                return null;
            });
            log.info("Redis flushDb executed successfully");

            codeListService.warmupCache();
            log.info("CodeList warmup completed successfully");

            return ResponseEntity.ok(Map.of(
                    "status", "SUCCESS",
                    "message", "Cache cleared and CodeLists refreshed successfully",
                    "timestamp", OffsetDateTime.now().toString()
            ));
        } catch (Exception e) {
            log.error("Failed to clear Redis cache: {}", e.getMessage(), e);
            return ResponseEntity.internalServerError().body(Map.of(
                    "status", "ERROR",
                    "message", "Failed to clear cache: " + e.getMessage(),
                    "timestamp", OffsetDateTime.now().toString()
            ));
        }
    }
}

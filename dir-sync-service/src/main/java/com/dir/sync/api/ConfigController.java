package com.dir.sync.api;

import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.dir.sync.config.DirJobProperties;

/**
 * Read and update the phase-flag configuration sourced from the D2 data
 * dictionary (xecm-sap-dir-service-bulk-config).
 *
 * GET  /api/config        — returns all current config key/value pairs
 * PATCH /api/config       — overrides one or more values in-memory for the
 *                           current process (does not write back to D2)
 */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private static final Logger logger = LogManager.getLogger(ConfigController.class);

    @Autowired private DirJobProperties props;

    @GetMapping
    public ResponseEntity<Map<String, String>> getAll() {
        return ResponseEntity.ok(props.getAll());
    }

    @PatchMapping
    public ResponseEntity<Map<String, Object>> patch(@RequestBody Map<String, String> updates) {
        updates.forEach((key, value) -> {
            props.set(key, value);
            logger.info("[CONFIG] {} => {}", key, value);
        });
        return ResponseEntity.ok(Map.of(
                "updated", updates.size(),
                "keys",    updates.keySet().toString()
        ));
    }
}

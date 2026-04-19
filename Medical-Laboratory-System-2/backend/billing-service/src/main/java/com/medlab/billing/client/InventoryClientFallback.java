package com.medlab.billing.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback when Inventory Service is unreachable.
 * Returns price = 0.0 so BillingService can handle it gracefully.
 */
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public Map<String, Object> getTestById(Long testId) {
        log.warn("InventoryService unavailable — returning fallback for testId={}", testId);
        return Map.of(
                "id", testId,
                "code", "UNKNOWN",
                "name", "Test not found (fallback)",
                "price", 0.0
        );
    }
}
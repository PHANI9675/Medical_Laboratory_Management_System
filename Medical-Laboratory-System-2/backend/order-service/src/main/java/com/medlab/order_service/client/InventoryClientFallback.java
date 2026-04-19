package com.medlab.order_service.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Fallback when Inventory Service is unreachable or returns an error.
 * Returns null so OrderService can skip the price lookup gracefully,
 * sending the notification without the estimated total instead of failing.
 */
@Component
@Slf4j
public class InventoryClientFallback implements InventoryClient {

    @Override
    public Map<String, Object> getTestById(Long id) {
        log.warn("InventoryService unavailable — cannot fetch price for testId={}", id);
        return null;
    }
}

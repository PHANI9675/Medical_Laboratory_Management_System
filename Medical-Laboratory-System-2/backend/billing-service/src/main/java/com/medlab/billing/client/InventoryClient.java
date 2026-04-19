package com.medlab.billing.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Calls Inventory & Catalog Service (P4 — Port 8084).
 * Used by BillingService to fetch the price of each test
 * when calculating the invoice total.
 *
 * Expected response from GET /tests/{id}:
 * { "id": 101, "code": "CBC", "name": "Complete Blood Count",
 *   "price": 12.50, "turnaroundHours": 24 }
 *
 * Resolved via Eureka when the full system is running.
 * For standalone testing, override:
 *   spring.cloud.openfeign.client.config.inventory-service.url=http://localhost:8084
 */
@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    @GetMapping("/tests/{id}")
    Map<String, Object> getTestById(@PathVariable("id") Long testId);
}
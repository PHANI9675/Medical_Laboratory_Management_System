package com.medlab.order_service.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Feign client to Inventory Service (Port 8084).
 *
 * Called by OrderService during order placement to fetch the price of each
 * ordered test, so the ORDER_PLACED notification can include the estimated total.
 *
 * Returns a generic Map so OrderService is not tightly coupled to Inventory's DTO.
 * Key used: "price" (BigDecimal).
 *
 * Fallback: InventoryClientFallback — returns null; OrderService gracefully omits
 * the amount from the notification if Inventory is unavailable.
 */
@FeignClient(name = "inventory-service", fallback = InventoryClientFallback.class)
public interface InventoryClient {

    @GetMapping("/tests/{id}")
    Map<String, Object> getTestById(@PathVariable("id") Long id);
}

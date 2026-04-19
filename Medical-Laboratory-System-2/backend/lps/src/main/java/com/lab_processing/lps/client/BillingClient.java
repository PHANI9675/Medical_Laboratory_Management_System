package com.lab_processing.lps.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;

/**
 * Feign client → Billing Service (Port 8085).
 * Called by ProcessingJobServiceImpl.approveResult() when a result is approved.
 *
 * No request body — orderId (path) is the only input.
 * Billing Service fetches all other data it needs from other services:
 *   - patientId + testIds  →  Order Service  GET /orders/{orderId}/detail
 *   - test prices          →  Inventory Service  GET /tests/{id}
 *
 * Fallback: BillingClientFallback — logs silently; result approval never blocks.
 */
@FeignClient(name = "billing-service", fallback = BillingClientFallback.class)
public interface BillingClient {

    @PostMapping("/billing/generate/{orderId}")
    void generateInvoice(@PathVariable("orderId") Long orderId);
}

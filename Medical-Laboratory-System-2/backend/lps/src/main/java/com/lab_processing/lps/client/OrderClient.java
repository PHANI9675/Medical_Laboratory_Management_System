package com.lab_processing.lps.client;

import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to Order Service (Port 8082).
 * Called by ProcessingJobServiceImpl.approveResult() to resolve
 * sampleId → real orderId + patientId + testIds before triggering billing.
 *
 * Fallback: OrderClientFallback — returns null so approveResult falls back
 * to using sampleId as a proxy (existing behaviour) rather than blocking.
 */
@FeignClient(name = "order-service", fallback = OrderClientFallback.class)
public interface OrderClient {

    @GetMapping("/orders/by-sample/{sampleId}")
    OrderDetailResponse getOrderBySampleId(@PathVariable("sampleId") Long sampleId);
}

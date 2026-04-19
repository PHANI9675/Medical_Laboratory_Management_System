package com.medlab.billing.client;

import com.medlab.billing.dto.OrderDetailResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client → Order Service (Port 8082).
 *
 * Called by BillingService.generateInvoice() to fetch the order details
 * (patientId + testIds) from Order Service using the orderId already present
 * in the billing request path.
 *
 * This is the "pull" model: Billing fetches the data it needs rather than
 * relying on LPS to push patientId/testIds in the request body.
 *
 * Fallback: OrderClientFallback — returns null; caller must handle null
 * and abort invoice generation (better than creating a corrupt invoice).
 */
@FeignClient(name = "order-service", fallback = OrderClientFallback.class)
public interface OrderClient {

    @GetMapping("/orders/{orderId}/detail")
    OrderDetailResponse getOrderDetailById(@PathVariable("orderId") Long orderId);
}

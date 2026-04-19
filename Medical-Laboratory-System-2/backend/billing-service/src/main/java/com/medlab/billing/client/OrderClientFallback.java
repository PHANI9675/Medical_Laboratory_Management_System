package com.medlab.billing.client;

import com.medlab.billing.dto.OrderDetailResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for OrderClient.
 *
 * Returns null when Order Service is unreachable.
 * BillingService.generateInvoice() checks for null and throws an exception,
 * preventing creation of invoices with incorrect/missing data.
 *
 * The exception propagates back to LPS, which catches it in its own
 * try-catch, logs the failure, and still returns a successful result-approval
 * response to the caller (resilience pattern).
 */
@Component
@Slf4j
public class OrderClientFallback implements OrderClient {

    @Override
    public OrderDetailResponse getOrderDetailById(Long orderId) {
        log.warn("OrderService unavailable — cannot fetch order details for orderId={}", orderId);
        return null;
    }
}

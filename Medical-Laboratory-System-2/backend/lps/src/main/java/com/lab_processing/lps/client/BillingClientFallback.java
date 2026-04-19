package com.lab_processing.lps.client;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for BillingClient.
 * If billing-service is unreachable, result approval still completes successfully.
 * A warning is logged so the operator knows billing was not triggered.
 */
@Component
@Slf4j
public class BillingClientFallback implements BillingClient {

    @Override
    public void generateInvoice(Long orderId) {
        log.warn("FALLBACK: BillingClient unavailable — invoice NOT generated for orderId={}", orderId);
    }
}

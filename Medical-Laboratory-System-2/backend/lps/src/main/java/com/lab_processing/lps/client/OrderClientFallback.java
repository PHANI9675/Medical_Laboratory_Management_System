package com.lab_processing.lps.client;

import org.springframework.stereotype.Component;

/**
 * Fallback when Order Service is unreachable.
 * Returns null so ProcessingJobServiceImpl.approveResult() can fall back
 * to using sampleId as the orderId/patientId proxy — result approval never blocks.
 */
@Component
public class OrderClientFallback implements OrderClient {

    @Override
    public OrderDetailResponse getOrderBySampleId(Long sampleId) {
        System.err.println("OrderService unavailable — using sampleId=" + sampleId
                + " as orderId/patientId fallback for billing");
        return null;
    }
}

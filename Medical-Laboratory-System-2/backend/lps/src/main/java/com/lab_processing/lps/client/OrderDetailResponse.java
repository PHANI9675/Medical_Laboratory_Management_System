package com.lab_processing.lps.client;

import java.util.List;

/**
 * DTO mirroring Order Service's OrderDetailResponse.
 * Returned by GET /orders/by-sample/{sampleId} so LPS can resolve
 * sampleId → real orderId, patientId, and testIds for billing.
 */
public class OrderDetailResponse {

    private Long orderId;
    private Long patientId;
    private List<Long> testIds;

    public OrderDetailResponse() {}

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public List<Long> getTestIds() { return testIds; }
    public void setTestIds(List<Long> testIds) { this.testIds = testIds; }
}

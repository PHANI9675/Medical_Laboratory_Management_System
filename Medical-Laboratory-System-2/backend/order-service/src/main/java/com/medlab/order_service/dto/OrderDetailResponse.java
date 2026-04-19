package com.medlab.order_service.dto;

import java.util.List;

/**
 * Response sent to LPS and Billing when looking up an Order.
 * Contains the fields needed for billing, result delivery, and notifications.
 *
 * sampleId is included so Billing Service can call LPS
 * GET /api/jobs/results/by-sample/{sampleId} to retrieve test results
 * and deliver them to the patient after payment.
 * sampleId is null when the sample has not yet been collected.
 */
public class OrderDetailResponse {

    private Long orderId;
    private Long patientId;
    private List<Long> testIds;
    private Long sampleId;   // null until sample is collected

    public OrderDetailResponse() {}

    public OrderDetailResponse(Long orderId, Long patientId, List<Long> testIds) {
        this.orderId = orderId;
        this.patientId = patientId;
        this.testIds = testIds;
    }

    public OrderDetailResponse(Long orderId, Long patientId, List<Long> testIds, Long sampleId) {
        this.orderId   = orderId;
        this.patientId = patientId;
        this.testIds   = testIds;
        this.sampleId  = sampleId;
    }

    public Long getOrderId() { return orderId; }
    public void setOrderId(Long orderId) { this.orderId = orderId; }

    public Long getPatientId() { return patientId; }
    public void setPatientId(Long patientId) { this.patientId = patientId; }

    public List<Long> getTestIds() { return testIds; }
    public void setTestIds(List<Long> testIds) { this.testIds = testIds; }

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }
}

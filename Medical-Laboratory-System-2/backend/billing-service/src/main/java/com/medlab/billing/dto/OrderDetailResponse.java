package com.medlab.billing.dto;

import java.util.List;

/**
 * DTO received from Order Service GET /orders/{orderId}/detail.
 * Mirrors com.medlab.order_service.dto.OrderDetailResponse.
 *
 * Used by BillingService.generateInvoice() to resolve:
 *   - patientId  → passed to PatientClient to get username for notification
 *   - testIds    → passed to InventoryClient to calculate invoice total
 *   - sampleId   → passed to LpsClient (PaymentService) to fetch test results
 *                  after payment so they can be sent as a LAB_RESULT notification
 */
public class OrderDetailResponse {

    private Long orderId;
    private Long patientId;
    private List<Long> testIds;
    private Long sampleId;   // null until sample is collected

    public OrderDetailResponse() {}

    public OrderDetailResponse(Long orderId, Long patientId, List<Long> testIds) {
        this.orderId   = orderId;
        this.patientId = patientId;
        this.testIds   = testIds;
    }

    public OrderDetailResponse(Long orderId, Long patientId, List<Long> testIds, Long sampleId) {
        this.orderId   = orderId;
        this.patientId = patientId;
        this.testIds   = testIds;
        this.sampleId  = sampleId;
    }

    public Long getOrderId()               { return orderId; }
    public void setOrderId(Long orderId)   { this.orderId = orderId; }

    public Long getPatientId()                 { return patientId; }
    public void setPatientId(Long patientId)   { this.patientId = patientId; }

    public List<Long> getTestIds()                 { return testIds; }
    public void setTestIds(List<Long> testIds)     { this.testIds = testIds; }

    public Long getSampleId()                  { return sampleId; }
    public void setSampleId(Long sampleId)     { this.sampleId = sampleId; }
}

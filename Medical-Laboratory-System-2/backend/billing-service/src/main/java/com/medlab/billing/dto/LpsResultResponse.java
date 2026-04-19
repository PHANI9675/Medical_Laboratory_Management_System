package com.medlab.billing.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDateTime;

/**
 * DTO mirroring LPS ResultResponse.
 * Returned by LPS GET /api/jobs/results/by-sample/{sampleId}.
 *
 * Used by PaymentService.notifyPaymentSuccess() to fetch the approved
 * lab result and deliver it to the patient as a LAB_RESULT notification
 * immediately after payment succeeds.
 *
 * @JsonIgnoreProperties — future LPS fields won't break deserialization.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class LpsResultResponse {

    private Long sampleId;
    private Long testId;
    private String result;      // raw JSON: {"value":5.6,"unit":"mg/dL"}
    private String status;      // ENTERED or APPROVED
    private LocalDateTime enteredAt;

    public LpsResultResponse() {}

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }

    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }

    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getEnteredAt() { return enteredAt; }
    public void setEnteredAt(LocalDateTime enteredAt) { this.enteredAt = enteredAt; }
}

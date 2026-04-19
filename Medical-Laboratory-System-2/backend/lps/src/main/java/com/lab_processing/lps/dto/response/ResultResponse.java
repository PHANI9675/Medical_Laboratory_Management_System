package com.lab_processing.lps.dto.response;

import java.time.LocalDateTime;

/**
 * Response DTO for a lab test result.
 *
 * Returned by GET /api/jobs/results/by-sample/{sampleId}.
 * Consumed by Billing Service after payment to deliver results to the patient
 * as a LAB_RESULT notification.
 *
 * Fields:
 *   sampleId  — echoed back so caller can correlate
 *   testId    — which test this result belongs to
 *   result    — raw JSON string entered by the lab technician
 *               e.g. {"value":5.6,"unit":"mg/dL"}
 *   status    — ENTERED or APPROVED
 *   enteredAt — when the result was recorded
 */
public class ResultResponse {

    private Long sampleId;
    private Long testId;
    private String result;
    private String status;
    private LocalDateTime enteredAt;

    public ResultResponse() {}

    public ResultResponse(Long sampleId, Long testId, String result,
                          String status, LocalDateTime enteredAt) {
        this.sampleId  = sampleId;
        this.testId    = testId;
        this.result    = result;
        this.status    = status;
        this.enteredAt = enteredAt;
    }

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

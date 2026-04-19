package com.medlab.order_service.dto;

/**
 * Request body sent to Lab Processing Service POST /api/jobs
 * to auto-create a ProcessingJob when a sample is collected.
 */
public class CreateJobRequest {

    private Long sampleId;
    private Long testId;

    public CreateJobRequest() {}

    public CreateJobRequest(Long sampleId, Long testId) {
        this.sampleId = sampleId;
        this.testId = testId;
    }

    public Long getSampleId() { return sampleId; }
    public void setSampleId(Long sampleId) { this.sampleId = sampleId; }

    public Long getTestId() { return testId; }
    public void setTestId(Long testId) { this.testId = testId; }
}

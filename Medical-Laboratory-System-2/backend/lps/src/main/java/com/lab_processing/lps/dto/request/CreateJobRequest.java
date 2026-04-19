package com.lab_processing.lps.dto.request;

import jakarta.validation.constraints.NotNull;

public class CreateJobRequest {

    @NotNull
    private Long sampleId;

    @NotNull
    private Long testId;

    public Long getSampleId() {
        return sampleId;
    }

    public void setSampleId(Long sampleId) {
        this.sampleId = sampleId;
    }

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
    }
}

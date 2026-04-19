package com.lab_processing.lps.dto.request;

import jakarta.validation.constraints.NotNull;

public class EnterResultRequest {

    @NotNull
    private Long testId;

    @NotNull
    private String result;
    // Example JSON string: {"value":5.6,"unit":"mg/dL"}

    @NotNull
    private Long enteredBy;

    public Long getTestId() {
        return testId;
    }

    public void setTestId(Long testId) {
        this.testId = testId;
    }

    public String getResult() {
        return result;
    }

    public void setResult(String result) {
        this.result = result;
    }

    public Long getEnteredBy() {
        return enteredBy;
    }

    public void setEnteredBy(Long enteredBy) {
        this.enteredBy = enteredBy;
    }
}

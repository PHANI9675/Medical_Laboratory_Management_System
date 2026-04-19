package com.medlab.order_service.client;

import com.medlab.order_service.dto.CreateJobRequest;
import org.springframework.stereotype.Component;

/**
 * Fallback when LPS is unreachable.
 * Logs a warning — job creation failure must NOT block sample collection.
 */
@Component
public class LpsClientFallback implements LpsClient {

    @Override
    public void createJob(CreateJobRequest request) {
        System.err.println("LPS unavailable — ProcessingJob NOT auto-created: sampleId="
                + request.getSampleId() + ", testId=" + request.getTestId());
    }
}

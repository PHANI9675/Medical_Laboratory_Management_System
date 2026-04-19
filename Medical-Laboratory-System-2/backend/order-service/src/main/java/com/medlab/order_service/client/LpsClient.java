package com.medlab.order_service.client;

import com.medlab.order_service.dto.CreateJobRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client to Lab Processing Service (LPS — Port 8083).
 * Called by OrderService.collectSample() to auto-create a ProcessingJob
 * for each test in the order once a sample has been collected.
 *
 * Fallback: LpsClientFallback — logs silently so sample collection
 * never blocks if LPS is temporarily unavailable.
 */
@FeignClient(name = "lps", fallback = LpsClientFallback.class)
public interface LpsClient {

    @PostMapping("/api/jobs")
    void createJob(@RequestBody CreateJobRequest request);
}

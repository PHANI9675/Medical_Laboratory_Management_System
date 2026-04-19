package com.medlab.billing.client;

import com.medlab.billing.dto.LpsResultResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client → Lab Processing Service (Eureka name: lps, Port 8083).
 *
 * Called by PaymentService.notifyPaymentSuccess() after a payment is confirmed.
 * Fetches the approved lab test result so it can be delivered to the patient
 * as a LAB_RESULT notification immediately after the PAYMENT_SUCCESS notification.
 *
 * Flow:
 *   PaymentService receives POST /payments (patient JWT)
 *     → marks invoice PAID
 *     → sends PAYMENT_SUCCESS notification
 *     → calls OrderClient to get sampleId from the invoice's orderId
 *     → calls this client: GET /api/jobs/results/by-sample/{sampleId}
 *     → sends LAB_RESULT notification with the formatted result
 *
 * The patient's JWT is forwarded by FeignClientConfig, so the LPS endpoint
 * must allow PATIENT authority (which it does).
 *
 * Fallback: LpsClientFallback — returns null; result notification is skipped
 * gracefully without affecting payment success.
 */
@FeignClient(name = "lps", fallback = LpsClientFallback.class)
public interface LpsClient {

    @GetMapping("/api/jobs/results/by-sample/{sampleId}")
    LpsResultResponse getResultBySampleId(@PathVariable("sampleId") Long sampleId);
}

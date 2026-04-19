package com.medlab.billing.client;

import com.medlab.billing.dto.LpsResultResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback for LpsClient.
 *
 * Returns null when LPS is unreachable or returns a non-2xx response.
 * PaymentService.notifyPaymentSuccess() checks for null and skips the
 * LAB_RESULT notification — payment itself is never affected.
 */
@Component
@Slf4j
public class LpsClientFallback implements LpsClient {

    @Override
    public LpsResultResponse getResultBySampleId(Long sampleId) {
        log.warn("LPS unavailable — cannot fetch result for sampleId={}, LAB_RESULT notification skipped",
                sampleId);
        return null;
    }
}

package com.medlab.billing.client;

import com.medlab.billing.dto.PatientResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when Patient Service is unreachable.
 * Returns null — BillingService and PaymentService skip the notification
 * gracefully rather than blocking invoice/payment operations.
 */
@Component
@Slf4j
public class PatientClientFallback implements PatientClient {

    @Override
    public PatientResponse getPatientById(Long patientId) {
        log.warn("PatientService unavailable — cannot resolve username for patientId={}, notification skipped",
                patientId);
        return null;
    }
}

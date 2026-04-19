package com.medlab.billing.client;

import com.medlab.billing.dto.PatientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Calls Patient Service (Port 8086) to resolve patientId → username.
 * Used by BillingService and PaymentService before sending notifications,
 * since Notification Service identifies patients by username, not patientId.
 *
 * Fallback: PatientClientFallback — returns null so notification is skipped
 * gracefully rather than blocking invoice generation or payment.
 */
@FeignClient(name = "patient-service", fallback = PatientClientFallback.class)
public interface PatientClient {

    @GetMapping("/patient/by-id/{patientId}")
    PatientResponse getPatientById(@PathVariable("patientId") Long patientId);
}

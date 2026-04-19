package com.medlab.order_service.client;

import com.medlab.order_service.dto.PatientResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when Patient Service is unreachable or returns non-2xx (e.g. 403).
 * Returns null so OrderService can handle each case gracefully.
 */
@Component
@Slf4j
public class PatientClientFallback implements PatientClient {

    @Override
    public PatientResponse getMyProfile() {
        log.warn("PatientService unavailable — cannot fetch current patient profile");
        return null;
    }

    @Override
    public PatientResponse getPatientById(Long patientId) {
        log.warn("PatientService unavailable — cannot fetch patient details for patientId={}", patientId);
        return null;
    }
}

package com.medlab.order_service.client;

import com.medlab.order_service.dto.PatientResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign client to Patient Service (Port 8086).
 *
 * Two endpoints used by OrderService:
 *
 * 1. GET /patient/profile  — called during order placement.
 *    Patient Service reads the username from the forwarded JWT (SecurityContext)
 *    and returns the caller's own profile including their DB id.
 *    Requires PATIENT authority → works when PATIENT places their own order.
 *    Returns patientId + username in one call — no patientId needed in request body.
 *
 * 2. GET /patient/by-id/{patientId}  — called during order cancellation.
 *    Requires ADMIN or LAB_TECH authority.
 *    When PATIENT cancels, this returns 403 → fallback to SecurityContextHolder.
 *
 * Fallback: PatientClientFallback — returns null so callers can handle gracefully.
 */
@FeignClient(name = "patient-service", fallback = PatientClientFallback.class)
public interface PatientClient {

    /**
     * Returns the profile of the currently authenticated patient.
     * Patient Service reads the username from the forwarded JWT — no path variable needed.
     * Requires PATIENT authority.
     */
    @GetMapping("/patient/profile")
    PatientResponse getMyProfile();

    /**
     * Returns a patient's profile by their DB id.
     * Requires ADMIN or LAB_TECH authority.
     */
    @GetMapping("/patient/by-id/{patientId}")
    PatientResponse getPatientById(@PathVariable("patientId") Long patientId);
}

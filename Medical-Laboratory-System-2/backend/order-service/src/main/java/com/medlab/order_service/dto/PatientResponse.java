package com.medlab.order_service.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * Subset of the Patient entity returned by Patient Service's GET /patient/by-id/{id}.
 * Only fields relevant to Order Service are mapped; unknown fields are ignored.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class PatientResponse {
    private Long id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private String phoneNumber;
}

package com.medlab.billing.dto;

/**
 * Subset of Patient Service's Patient entity used by Billing
 * to resolve patientId → username for sending notifications.
 */
public class PatientResponse {

    private Long id;
    private String username;

    public PatientResponse() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }
}

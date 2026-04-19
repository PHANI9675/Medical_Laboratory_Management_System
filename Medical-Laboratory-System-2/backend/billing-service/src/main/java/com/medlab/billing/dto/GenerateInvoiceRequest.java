package com.medlab.billing.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

@Data
public class GenerateInvoiceRequest {

    /** Set from the path variable in the controller */
    private Long orderId;

    /** Patient's userId in the User Service — required */
    @NotNull(message = "patientId is required")
    private Long patientId;

    /**
     * List of test IDs in this order.
     * BillingService calls Inventory Service for each to calculate the total.
     */
    private List<Long> testIds;
}
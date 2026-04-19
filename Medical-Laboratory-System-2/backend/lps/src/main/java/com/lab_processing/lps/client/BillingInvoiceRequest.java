package com.lab_processing.lps.client;

/**
 * DEPRECATED — no longer used.
 *
 * Previously sent patientId + testIds to Billing Service.
 * Now Billing fetches order details itself from Order Service
 * using the orderId already present in the request path.
 *
 * File kept for reference; class has no fields or Spring annotations.
 */
public class BillingInvoiceRequest {
    // No longer used — see BillingClient and BillingService for the new pull model.
}

package com.medlab.billing.controller;

import com.medlab.billing.dto.InvoiceResponse;
import com.medlab.billing.service.BillingService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Billing", description = "Invoice generation and retrieval")
public class BillingController {

    private final BillingService billingService;

    /**
     * Triggered by Lab Processing Service when a result is approved.
     *
     * No request body — orderId (path) is the only input.
     * Billing fetches all required data itself from other services:
     *   - patientId + testIds  →  Order Service      GET /orders/{orderId}/detail
     *   - test prices          →  Inventory Service   GET /tests/{id}
     *   - patient username     →  Patient Service     GET /patient/by-id/{patientId}
     *   - notification         →  Notification Service POST /notification
     */
    @PostMapping("/billing/generate/{orderId}")
    @PreAuthorize("hasAuthority('ADMIN')")
    @Operation(
            summary = "Generate invoice for an approved order",
            description = "Triggered by Lab Processing Service when a result is approved. " +
                    "No request body needed — Billing fetches order details from Order Service, " +
                    "test prices from Inventory Service, and notifies patient via Notification Service."
    )
    public ResponseEntity<InvoiceResponse> generateInvoice(@PathVariable Long orderId) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(billingService.generateInvoice(orderId));
    }

    @GetMapping("/invoices/{id}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PHYSICIAN', 'LAB_TECH', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Get invoice by ID")
    public ResponseEntity<InvoiceResponse> getInvoiceById(@PathVariable Long id) {
        return ResponseEntity.ok(billingService.getInvoiceById(id));
    }

    @GetMapping("/invoices/order/{orderId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PHYSICIAN', 'LAB_TECH', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Get invoice by order ID")
    public ResponseEntity<InvoiceResponse> getInvoiceByOrderId(@PathVariable Long orderId) {
        return ResponseEntity.ok(billingService.getInvoiceByOrderId(orderId));
    }

    @GetMapping("/invoices/patient/{patientId}")
    @PreAuthorize("hasAnyAuthority('ADMIN', 'PHYSICIAN', 'RECEPTIONIST', 'PATIENT')")
    @Operation(summary = "Get all invoices for a patient")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByPatient(
            @PathVariable Long patientId) {
        return ResponseEntity.ok(billingService.getInvoicesByPatient(patientId));
    }
}

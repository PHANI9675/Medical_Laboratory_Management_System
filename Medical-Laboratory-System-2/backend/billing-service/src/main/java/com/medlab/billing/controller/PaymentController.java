package com.medlab.billing.controller;

import com.medlab.billing.dto.PaymentRequest;
import com.medlab.billing.dto.PaymentResponse;
import com.medlab.billing.service.PaymentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@Tag(name = "Payments", description = "Mocked payment processing and payment history")
public class PaymentController {

    private final PaymentService paymentService;

    @PostMapping("/payments")
    // FIX: hasAnyRole() prepends ROLE_ automatically — inconsistent with our JwtFilter
    // which stores plain role strings. Switched to hasAnyAuthority() throughout.
    @PreAuthorize("hasAnyAuthority('PATIENT', 'ADMIN', 'RECEPTIONIST')")
    @Operation(
            summary = "Submit payment (mocked — always succeeds)",
            description = "Accepts CREDIT_CARD, DEBIT_CARD, or UPI. " +
                    "No real gateway is invoked. Invoice is marked PAID immediately. " +
                    "Patient is notified via User Service."
    )
    public ResponseEntity<PaymentResponse> processPayment(
            @Valid @RequestBody PaymentRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(paymentService.processPayment(request));
    }

    @GetMapping("/payments/{invoiceId}")
    @PreAuthorize("hasAnyAuthority('PATIENT', 'ADMIN', 'RECEPTIONIST', 'PHYSICIAN')")
    @Operation(summary = "Get payment history for an invoice")
    public ResponseEntity<List<PaymentResponse>> getPaymentsByInvoice(
            @PathVariable Long invoiceId) {
        return ResponseEntity.ok(paymentService.getPaymentsByInvoice(invoiceId));
    }
}
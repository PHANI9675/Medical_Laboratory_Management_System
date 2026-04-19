package com.medlab.billing.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.medlab.billing.dto.PaymentRequest;
import com.medlab.billing.dto.PaymentResponse;
import com.medlab.billing.exception.GlobalExceptionHandler;
import com.medlab.billing.exception.InvalidPaymentStateException;
import com.medlab.billing.exception.ResourceNotFoundException;
import com.medlab.billing.model.PaymentMethod;
import com.medlab.billing.model.PaymentStatus;
import com.medlab.billing.service.PaymentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(PaymentController.class)
@Import(GlobalExceptionHandler.class)
class PaymentControllerTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean PaymentService paymentService;

    private PaymentResponse samplePaymentResponse() {
        return PaymentResponse.builder()
                .transactionId("TXN-20260401-0001").invoiceId(1L)
                .amount(new BigDecimal("45.00")).paymentMethod(PaymentMethod.UPI)
                .paymentStatus(PaymentStatus.PAID).paidAt(LocalDateTime.now())
                .message("Payment successful. Transaction ID: TXN-20260401-0001").build();
    }

    private PaymentRequest buildRequest(Long invoiceId, PaymentMethod method, String amount) {
        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(invoiceId);
        req.setPaymentMethod(method);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /payments: returns 201 with PAID status when PATIENT submits payment")
    void processPayment_patient_returns201() throws Exception {
        when(paymentService.processPayment(any(PaymentRequest.class)))
                .thenReturn(samplePaymentResponse());

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest(1L, PaymentMethod.UPI, "45.00"))))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.transactionId").value("TXN-20260401-0001"))
                .andExpect(jsonPath("$.amount").value(45.00))
                .andExpect(jsonPath("$.paymentMethod").value("UPI"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /payments: returns 201 when ADMIN submits payment on behalf")
    void processPayment_admin_returns201() throws Exception {
        when(paymentService.processPayment(any())).thenReturn(samplePaymentResponse());

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest(1L, PaymentMethod.CREDIT_CARD, "45.00"))))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "PHYSICIAN")
    @DisplayName("POST /payments: returns 403 when PHYSICIAN tries to submit payment")
    void processPayment_physician_returns403() throws Exception {
        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest(1L, PaymentMethod.UPI, "45.00"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /payments: returns 422 when invoice is already PAID")
    void processPayment_alreadyPaid_returns422() throws Exception {
        when(paymentService.processPayment(any()))
                .thenThrow(new InvalidPaymentStateException(
                        "Invoice INV-2026-0001 is already PAID."));

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest(1L, PaymentMethod.UPI, "45.00"))))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.message").value("Invoice INV-2026-0001 is already PAID."));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /payments: returns 404 when invoice does not exist")
    void processPayment_invoiceNotFound_returns404() throws Exception {
        when(paymentService.processPayment(any()))
                .thenThrow(new ResourceNotFoundException("Invoice", "id", 99L));

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                buildRequest(99L, PaymentMethod.DEBIT_CARD, "45.00"))))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /payments: returns 400 when required fields are missing")
    void processPayment_missingFields_returns400() throws Exception {
        PaymentRequest invalid = new PaymentRequest();

        mockMvc.perform(post("/payments")
                        .with(csrf())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /payments: accepts all three payment methods")
    void processPayment_allMethods_return201() throws Exception {
        for (PaymentMethod method : PaymentMethod.values()) {
            PaymentResponse resp = PaymentResponse.builder()
                    .transactionId("TXN-0001").invoiceId(1L)
                    .amount(new BigDecimal("45.00")).paymentMethod(method)
                    .paymentStatus(PaymentStatus.PAID).paidAt(LocalDateTime.now())
                    .message("Payment successful. Transaction ID: TXN-0001").build();

            when(paymentService.processPayment(any())).thenReturn(resp);

            mockMvc.perform(post("/payments")
                            .with(csrf())
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(
                                    buildRequest(1L, method, "45.00"))))
                    .andExpect(status().isCreated())
                    .andExpect(jsonPath("$.paymentStatus").value("PAID"))
                    .andExpect(jsonPath("$.paymentMethod").value(method.name()));
        }
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /payments/{invoiceId}: returns 200 with payment history list")
    void getPaymentsByInvoice_returns200() throws Exception {
        when(paymentService.getPaymentsByInvoice(1L)).thenReturn(List.of(samplePaymentResponse()));

        mockMvc.perform(get("/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].transactionId").value("TXN-20260401-0001"))
                .andExpect(jsonPath("$[0].paymentStatus").value("PAID"))
                .andExpect(jsonPath("$[0].paymentMethod").value("UPI"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /payments/{invoiceId}: returns empty list when no payments made yet")
    void getPaymentsByInvoice_empty() throws Exception {
        when(paymentService.getPaymentsByInvoice(1L)).thenReturn(List.of());

        mockMvc.perform(get("/payments/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /payments/{invoiceId}: returns 404 when invoice does not exist")
    void getPaymentsByInvoice_invoiceNotFound_returns404() throws Exception {
        when(paymentService.getPaymentsByInvoice(99L))
                .thenThrow(new ResourceNotFoundException("Invoice", "id", 99L));

        mockMvc.perform(get("/payments/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    @DisplayName("GET /payments/{invoiceId}: returns 403 when LAB_TECH tries to view payment history")
    void getPaymentsByInvoice_labTech_returns403() throws Exception {
        mockMvc.perform(get("/payments/1"))
                .andExpect(status().isForbidden());
    }
}
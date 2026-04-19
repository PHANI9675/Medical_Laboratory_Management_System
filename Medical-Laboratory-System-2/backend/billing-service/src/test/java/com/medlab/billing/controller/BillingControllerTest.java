package com.medlab.billing.controller;

import com.medlab.billing.dto.InvoiceResponse;
import com.medlab.billing.exception.GlobalExceptionHandler;
import com.medlab.billing.exception.InvoiceAlreadyExistsException;
import com.medlab.billing.exception.ResourceNotFoundException;
import com.medlab.billing.model.InvoiceStatus;
import com.medlab.billing.service.BillingService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(BillingController.class)
@Import(GlobalExceptionHandler.class)
class BillingControllerTest {

    @Autowired MockMvc mockMvc;
    @MockitoBean BillingService billingService;

    private InvoiceResponse sampleResponse() {
        return InvoiceResponse.builder()
                .id(1L).invoiceNumber("INV-2026-0001").orderId(1001L).patientId(10L)
                .amount(new BigDecimal("45.00")).currency("INR").status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(10)).createdAt(LocalDateTime.now()).build();
    }

    // ── POST /billing/generate/{orderId} ──────────────────────────────────────
    // No request body — orderId in path is the only input.
    // Billing fetches patientId+testIds from Order Service itself.

    @Test
    @WithMockUser(roles = "LAB_TECH")
    @DisplayName("POST /billing/generate/{orderId}: returns 201 when LAB_TECH calls it (no body)")
    void generateInvoice_labTech_returns201() throws Exception {
        when(billingService.generateInvoice(1001L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/billing/generate/1001").with(csrf()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.invoiceNumber").value("INV-2026-0001"))
                .andExpect(jsonPath("$.amount").value(45.00))
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.currency").value("INR"))
                .andExpect(jsonPath("$.patientId").value(10));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("POST /billing/generate/{orderId}: returns 201 when ADMIN calls it (no body)")
    void generateInvoice_admin_returns201() throws Exception {
        when(billingService.generateInvoice(1001L)).thenReturn(sampleResponse());

        mockMvc.perform(post("/billing/generate/1001").with(csrf()))
                .andExpect(status().isCreated());
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("POST /billing/generate/{orderId}: returns 403 when PATIENT tries to generate invoice")
    void generateInvoice_patient_returns403() throws Exception {
        mockMvc.perform(post("/billing/generate/1001").with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "LAB_TECH")
    @DisplayName("POST /billing/generate/{orderId}: returns 409 when invoice already exists for order")
    void generateInvoice_duplicate_returns409() throws Exception {
        when(billingService.generateInvoice(1001L))
                .thenThrow(new InvoiceAlreadyExistsException(
                        "Invoice already exists for orderId=1001 → INV-2026-0001"));

        mockMvc.perform(post("/billing/generate/1001").with(csrf()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Conflict"))
                .andExpect(jsonPath("$.message").exists());
    }

    // ── GET /invoices/{id} ────────────────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /invoices/{id}: returns 200 with invoice details")
    void getInvoiceById_returns200() throws Exception {
        when(billingService.getInvoiceById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/invoices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(1))
                .andExpect(jsonPath("$.invoiceNumber").value("INV-2026-0001"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /invoices/{id}: returns 404 when invoice does not exist")
    void getInvoiceById_notFound_returns404() throws Exception {
        when(billingService.getInvoiceById(99L))
                .thenThrow(new ResourceNotFoundException("Invoice", "id", 99L));

        mockMvc.perform(get("/invoices/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Not Found"));
    }

    // ── GET /invoices/order/{orderId} ─────────────────────────────────────────

    @Test
    @WithMockUser(roles = "PHYSICIAN")
    @DisplayName("GET /invoices/order/{orderId}: returns invoice for the given order")
    void getInvoiceByOrderId_returns200() throws Exception {
        when(billingService.getInvoiceByOrderId(1001L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/invoices/order/1001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.orderId").value(1001));
    }

    // ── GET /invoices/patient/{patientId} ─────────────────────────────────────

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /invoices/patient/{patientId}: returns list of invoices for the patient")
    void getInvoicesByPatient_returnsList() throws Exception {
        when(billingService.getInvoicesByPatient(10L)).thenReturn(List.of(sampleResponse()));

        mockMvc.perform(get("/invoices/patient/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$[0].patientId").value(10))
                .andExpect(jsonPath("$[0].invoiceNumber").value("INV-2026-0001"));
    }

    @Test
    @WithMockUser(roles = "PATIENT")
    @DisplayName("GET /invoices/patient/{patientId}: returns empty array when patient has no invoices")
    void getInvoicesByPatient_empty() throws Exception {
        when(billingService.getInvoicesByPatient(10L)).thenReturn(List.of());

        mockMvc.perform(get("/invoices/patient/10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray())
                .andExpect(jsonPath("$").isEmpty());
    }
}

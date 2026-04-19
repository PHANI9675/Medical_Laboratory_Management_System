package com.medlab.billing.service;

import com.medlab.billing.client.InventoryClient;
import com.medlab.billing.client.NotificationClient;
import com.medlab.billing.client.OrderClient;
import com.medlab.billing.client.PatientClient;
import com.medlab.billing.dto.InvoiceResponse;
import com.medlab.billing.dto.OrderDetailResponse;
import com.medlab.billing.dto.PatientResponse;
import com.medlab.billing.exception.InvoiceAlreadyExistsException;
import com.medlab.billing.exception.InvalidPaymentStateException;
import com.medlab.billing.exception.ResourceNotFoundException;
import com.medlab.billing.model.Invoice;
import com.medlab.billing.model.InvoiceStatus;
import com.medlab.billing.repository.InvoiceRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BillingServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private InventoryClient inventoryClient;
    @Mock private PatientClient patientClient;
    @Mock private NotificationClient notificationClient;
    @Mock private OrderClient orderClient;
    @Mock private InvoiceNumberGenerator invoiceNumberGen;

    @InjectMocks
    private BillingService billingService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Invoice pendingInvoice(Long id, String number) {
        return Invoice.builder()
                .id(id).invoiceNumber(number).orderId(id).patientId(10L)
                .amount(new BigDecimal("45.00")).currency("INR")
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(10))
                .createdAt(LocalDateTime.now()).build();
    }

    private OrderDetailResponse orderDetail(List<Long> testIds) {
        return new OrderDetailResponse(1L, 10L, testIds);
    }

    private PatientResponse patientResponse() {
        PatientResponse p = new PatientResponse();
        p.setId(10L);
        p.setUsername("patient@lab.com");
        return p;
    }

    // ── generateInvoice ───────────────────────────────────────────────────────

    @Test
    @DisplayName("generateInvoice: fetches order details from Order Service, calculates total, saves invoice")
    void generateInvoice_fetchesOrderDetails_calculatesTotal_andSaves() {
        when(invoiceRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderClient.getOrderDetailById(1L)).thenReturn(orderDetail(List.of(101L, 102L)));
        when(invoiceNumberGen.next()).thenReturn("INV-2026-0001");
        when(inventoryClient.getTestById(101L)).thenReturn(Map.of("price", 12.50));
        when(inventoryClient.getTestById(102L)).thenReturn(Map.of("price", 32.50));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(pendingInvoice(1L, "INV-2026-0001"));
        when(patientClient.getPatientById(10L)).thenReturn(patientResponse());

        InvoiceResponse response = billingService.generateInvoice(1L);

        assertThat(response.getInvoiceNumber()).isEqualTo("INV-2026-0001");
        assertThat(response.getAmount()).isEqualByComparingTo("45.00");
        assertThat(response.getStatus()).isEqualTo(InvoiceStatus.PENDING);
        assertThat(response.getCurrency()).isEqualTo("INR");
        assertThat(response.getOrderId()).isEqualTo(1L);
        assertThat(response.getPatientId()).isEqualTo(10L);
        verify(invoiceRepository).save(any(Invoice.class));
        verify(orderClient).getOrderDetailById(1L);
    }

    @Test
    @DisplayName("generateInvoice: sends INVOICE_GENERATED notification via Notification Service")
    void generateInvoice_sendsNotificationToPatient() {
        when(invoiceRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderClient.getOrderDetailById(1L)).thenReturn(orderDetail(List.of(101L, 102L)));
        when(invoiceNumberGen.next()).thenReturn("INV-2026-0001");
        when(inventoryClient.getTestById(101L)).thenReturn(Map.of("price", 12.50));
        when(inventoryClient.getTestById(102L)).thenReturn(Map.of("price", 32.50));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(pendingInvoice(1L, "INV-2026-0001"));
        when(patientClient.getPatientById(10L)).thenReturn(patientResponse());

        billingService.generateInvoice(1L);

        verify(notificationClient).createNotification(argThat(n ->
                n.getUsername().equals("patient@lab.com") &&
                n.getType().equals("INVOICE_GENERATED") &&
                n.getMessage().contains("INV-2026-0001")
        ));
    }

    @Test
    @DisplayName("generateInvoice: notification failure does NOT prevent invoice creation")
    void generateInvoice_notificationFailure_doesNotBlock() {
        when(invoiceRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderClient.getOrderDetailById(1L)).thenReturn(orderDetail(List.of(101L)));
        when(invoiceNumberGen.next()).thenReturn("INV-2026-0001");
        when(inventoryClient.getTestById(101L)).thenReturn(Map.of("price", 45.00));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(pendingInvoice(1L, "INV-2026-0001"));
        when(patientClient.getPatientById(10L)).thenReturn(patientResponse());
        doThrow(new RuntimeException("Notification Service down"))
                .when(notificationClient).createNotification(any());

        assertThatCode(() -> billingService.generateInvoice(1L))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("generateInvoice: throws InvoiceAlreadyExistsException when order already has invoice")
    void generateInvoice_duplicateOrder_throws() {
        when(invoiceRepository.findByOrderId(1L))
                .thenReturn(Optional.of(pendingInvoice(1L, "INV-2026-0001")));

        assertThatThrownBy(() -> billingService.generateInvoice(1L))
                .isInstanceOf(InvoiceAlreadyExistsException.class)
                .hasMessageContaining("INV-2026-0001");

        verify(invoiceRepository, never()).save(any());
        // Order Service never called — idempotency check fires first
        verify(orderClient, never()).getOrderDetailById(any());
    }

    @Test
    @DisplayName("generateInvoice: throws when Order Service is unavailable (null response)")
    void generateInvoice_orderServiceUnavailable_throws() {
        when(invoiceRepository.findByOrderId(1L)).thenReturn(Optional.empty());
        when(orderClient.getOrderDetailById(1L)).thenReturn(null); // fallback returns null

        assertThatThrownBy(() -> billingService.generateInvoice(1L))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Order Service unavailable");

        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("generateInvoice: total is zero when order has no testIds")
    void generateInvoice_noTestIds_totalIsZero() {
        when(invoiceRepository.findByOrderId(2L)).thenReturn(Optional.empty());
        when(orderClient.getOrderDetailById(2L)).thenReturn(orderDetail(null));
        when(invoiceNumberGen.next()).thenReturn("INV-2026-0002");

        Invoice zeroInvoice = Invoice.builder()
                .id(2L).invoiceNumber("INV-2026-0002").orderId(2L).patientId(10L)
                .amount(BigDecimal.ZERO).currency("INR").status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(10)).createdAt(LocalDateTime.now()).build();
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(zeroInvoice);
        when(patientClient.getPatientById(10L)).thenReturn(patientResponse());

        InvoiceResponse response = billingService.generateInvoice(2L);
        assertThat(response.getAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        verifyNoInteractions(inventoryClient);
    }

    // ── getInvoiceById ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvoiceById: returns mapped InvoiceResponse when invoice exists")
    void getInvoiceById_found() {
        when(invoiceRepository.findById(5L))
                .thenReturn(Optional.of(pendingInvoice(5L, "INV-2026-0005")));

        InvoiceResponse res = billingService.getInvoiceById(5L);

        assertThat(res.getId()).isEqualTo(5L);
        assertThat(res.getInvoiceNumber()).isEqualTo("INV-2026-0005");
        assertThat(res.getStatus()).isEqualTo(InvoiceStatus.PENDING);
    }

    @Test
    @DisplayName("getInvoiceById: throws ResourceNotFoundException when invoice does not exist")
    void getInvoiceById_notFound_throws() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.getInvoiceById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    // ── getInvoiceByOrderId ───────────────────────────────────────────────────

    @Test
    @DisplayName("getInvoiceByOrderId: returns invoice matched to orderId")
    void getInvoiceByOrderId_found() {
        when(invoiceRepository.findByOrderId(10L))
                .thenReturn(Optional.of(pendingInvoice(10L, "INV-2026-0010")));

        InvoiceResponse res = billingService.getInvoiceByOrderId(10L);
        assertThat(res.getOrderId()).isEqualTo(10L);
    }

    @Test
    @DisplayName("getInvoiceByOrderId: throws ResourceNotFoundException when no invoice for order")
    void getInvoiceByOrderId_notFound_throws() {
        when(invoiceRepository.findByOrderId(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.getInvoiceByOrderId(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    // ── markPaid ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("markPaid: transitions PENDING invoice to PAID and persists it")
    void markPaid_pendingToPaid() {
        Invoice inv = pendingInvoice(1L, "INV-2026-0001");
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));
        when(invoiceRepository.save(inv)).thenReturn(inv);

        Invoice result = billingService.markPaid(1L);

        assertThat(result.getStatus()).isEqualTo(InvoiceStatus.PAID);
        verify(invoiceRepository).save(inv);
    }

    @Test
    @DisplayName("markPaid: throws InvalidPaymentStateException when invoice is already PAID")
    void markPaid_alreadyPaid_throws() {
        Invoice inv = Invoice.builder().id(1L).status(InvoiceStatus.PAID)
                .invoiceNumber("INV-2026-0001").build();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> billingService.markPaid(1L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("already PAID");
    }

    @Test
    @DisplayName("markPaid: throws InvalidPaymentStateException when invoice is CANCELLED")
    void markPaid_cancelled_throws() {
        Invoice inv = Invoice.builder().id(1L).status(InvoiceStatus.CANCELLED)
                .invoiceNumber("INV-2026-0001").build();
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(inv));

        assertThatThrownBy(() -> billingService.markPaid(1L))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("CANCELLED");
    }

    @Test
    @DisplayName("markPaid: throws ResourceNotFoundException when invoice id does not exist")
    void markPaid_invoiceNotFound_throws() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> billingService.markPaid(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}

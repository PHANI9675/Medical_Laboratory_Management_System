package com.medlab.billing.service;

import com.medlab.billing.client.LpsClient;
import com.medlab.billing.client.NotificationClient;
import com.medlab.billing.client.OrderClient;
import com.medlab.billing.client.PatientClient;
import com.medlab.billing.dto.NotificationSendRequest;
import com.medlab.billing.dto.PatientResponse;
import com.medlab.billing.dto.PaymentRequest;
import com.medlab.billing.dto.PaymentResponse;
import com.medlab.billing.exception.InvalidPaymentAmountException;
import com.medlab.billing.exception.InvalidPaymentStateException;
import com.medlab.billing.model.*;
import com.medlab.billing.repository.PaymentRepository;
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

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private BillingService billingService;
    @Mock private PatientClient patientClient;
    @Mock private NotificationClient notificationClient;
    @Mock private OrderClient orderClient;
    @Mock private LpsClient lpsClient;
    @Mock private TransactionIdGenerator txnIdGen;

    @InjectMocks
    private PaymentService paymentService;

    // ── Helpers ──────────────────────────────────────────────────────────────

    private Invoice pendingInvoice(Long id, Long patientId) {
        return Invoice.builder()
                .id(id).patientId(patientId)
                .invoiceNumber("INV-2026-000" + id)
                .amount(new BigDecimal("45.00"))
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now())
                .createdAt(LocalDateTime.now()).build();
    }

    private PaymentRequest buildRequest(Long invoiceId, PaymentMethod method, String amount) {
        PaymentRequest req = new PaymentRequest();
        req.setInvoiceId(invoiceId);
        req.setPaymentMethod(method);
        req.setAmount(new BigDecimal(amount));
        return req;
    }

    private PatientResponse patientResponse(Long id, String username) {
        PatientResponse p = new PatientResponse();
        p.setId(id);
        p.setUsername(username);
        return p;
    }

    // ── processPayment — happy path ───────────────────────────────────────────

    @Test
    @DisplayName("processPayment: always records PAID status (mocked gateway)")
    void processPayment_alwaysReturnsPaid() {
        PaymentRequest req = buildRequest(1L, PaymentMethod.UPI, "45.00");
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(billingService.markPaid(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(txnIdGen.next()).thenReturn("TXN-20260401-0001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse response = paymentService.processPayment(req);

        assertThat(response.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(response.getTransactionId()).isEqualTo("TXN-20260401-0001");
        assertThat(response.getAmount()).isEqualByComparingTo("45.00");
        assertThat(response.getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
        assertThat(response.getInvoiceId()).isEqualTo(1L);
        assertThat(response.getMessage()).contains("TXN-20260401-0001");
        assertThat(response.getPaidAt()).isNotNull();
    }

    @Test
    @DisplayName("processPayment: persists payment record with correct fields")
    void processPayment_savesPaymentRecord() {
        // CREDIT_CARD with 45.00 — well within the ₹40,000 card limit
        PaymentRequest req = buildRequest(1L, PaymentMethod.CREDIT_CARD, "45.00");
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(billingService.markPaid(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(txnIdGen.next()).thenReturn("TXN-20260401-0001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        paymentService.processPayment(req);

        verify(paymentRepository).save(argThat(p ->
                p.getInvoiceId().equals(1L) &&
                p.getPaymentMethod() == PaymentMethod.CREDIT_CARD &&
                p.getStatus() == PaymentStatus.PAID &&
                p.getTransactionId().equals("TXN-20260401-0001") &&
                p.getAmountPaid().compareTo(new BigDecimal("45.00")) == 0
        ));
    }

    @Test
    @DisplayName("processPayment: sends PAYMENT_SUCCESS notification to patient via NotificationClient")
    void processPayment_sendsPaymentSuccessNotification() {
        PaymentRequest req = buildRequest(1L, PaymentMethod.DEBIT_CARD, "45.00");
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(billingService.markPaid(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(txnIdGen.next()).thenReturn("TXN-20260401-0001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(patientClient.getPatientById(10L)).thenReturn(patientResponse(10L, "patient@lab.com"));
        // orderClient returns null by default → LAB_RESULT notification skipped gracefully

        paymentService.processPayment(req);

        verify(notificationClient).createNotification(argThat(n ->
                n.getUsername().equals("patient@lab.com") &&
                n.getType().equals("PAYMENT_SUCCESS") &&
                n.getMessage().contains("TXN-20260401-0001")
        ));
    }

    @Test
    @DisplayName("processPayment: notification failure does NOT prevent payment from succeeding")
    void processPayment_notificationFailure_doesNotBlock() {
        // Amount must match invoice (45.00) to pass the amount-mismatch guard.
        PaymentRequest req = buildRequest(2L, PaymentMethod.UPI, "45.00");
        when(billingService.findInvoiceOrThrow(2L)).thenReturn(pendingInvoice(2L, 20L));
        when(billingService.markPaid(2L)).thenReturn(pendingInvoice(2L, 20L));
        when(txnIdGen.next()).thenReturn("TXN-20260401-0002");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));
        when(patientClient.getPatientById(20L)).thenReturn(patientResponse(20L, "other@lab.com"));
        doThrow(new RuntimeException("Notification Service unavailable"))
                .when(notificationClient).createNotification(any(NotificationSendRequest.class));

        assertThatCode(() -> paymentService.processPayment(req))
                .doesNotThrowAnyException();
    }

    // ── processPayment — guard tests ──────────────────────────────────────────

    @Test
    @DisplayName("processPayment: throws InvalidPaymentAmountException when amount does not match invoice")
    void processPayment_amountMismatch_throws400() {
        // Invoice is ₹45.00 but patient sends ₹99.00
        PaymentRequest req = buildRequest(1L, PaymentMethod.UPI, "99.00");
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));

        assertThatThrownBy(() -> paymentService.processPayment(req))
                .isInstanceOf(InvalidPaymentAmountException.class)
                .hasMessageContaining("does not match invoice amount");

        verify(paymentRepository, never()).save(any());
        verify(billingService, never()).markPaid(any());
        verify(notificationClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("processPayment: throws InvalidPaymentStateException when CREDIT_CARD amount exceeds ₹40,000")
    void processPayment_creditCardOverLimit_throws422() {
        // Build a large invoice to avoid the amount-mismatch guard first
        Invoice bigInvoice = Invoice.builder()
                .id(5L).patientId(10L).invoiceNumber("INV-BIG")
                .amount(new BigDecimal("50000.00"))
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now()).createdAt(LocalDateTime.now()).build();

        PaymentRequest req = buildRequest(5L, PaymentMethod.CREDIT_CARD, "50000.00");
        when(billingService.findInvoiceOrThrow(5L)).thenReturn(bigInvoice);

        assertThatThrownBy(() -> paymentService.processPayment(req))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("exceeds the card transaction limit");

        verify(paymentRepository, never()).save(any());
        verify(billingService, never()).markPaid(any());
    }

    @Test
    @DisplayName("processPayment: throws InvalidPaymentStateException when DEBIT_CARD amount exceeds ₹40,000")
    void processPayment_debitCardOverLimit_throws422() {
        Invoice bigInvoice = Invoice.builder()
                .id(6L).patientId(10L).invoiceNumber("INV-BIG2")
                .amount(new BigDecimal("41000.00"))
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now()).createdAt(LocalDateTime.now()).build();

        PaymentRequest req = buildRequest(6L, PaymentMethod.DEBIT_CARD, "41000.00");
        when(billingService.findInvoiceOrThrow(6L)).thenReturn(bigInvoice);

        assertThatThrownBy(() -> paymentService.processPayment(req))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("exceeds the card transaction limit");

        verify(paymentRepository, never()).save(any());
    }

    @Test
    @DisplayName("processPayment: UPI has no card limit — ₹50,000 via UPI succeeds when amounts match")
    void processPayment_upiNoCardLimit() {
        Invoice bigInvoice = Invoice.builder()
                .id(7L).patientId(10L).invoiceNumber("INV-UPI-BIG")
                .amount(new BigDecimal("50000.00"))
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now()).createdAt(LocalDateTime.now()).build();

        PaymentRequest req = buildRequest(7L, PaymentMethod.UPI, "50000.00");
        when(billingService.findInvoiceOrThrow(7L)).thenReturn(bigInvoice);
        when(billingService.markPaid(7L)).thenReturn(bigInvoice);
        when(txnIdGen.next()).thenReturn("TXN-BIG-001");
        when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

        PaymentResponse res = paymentService.processPayment(req);

        assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(res.getAmount()).isEqualByComparingTo("50000.00");
    }

    @Test
    @DisplayName("processPayment: propagates InvalidPaymentStateException when invoice is already PAID")
    void processPayment_alreadyPaidInvoice_throws() {
        PaymentRequest req = buildRequest(1L, PaymentMethod.UPI, "45.00");
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(billingService.markPaid(1L))
                .thenThrow(new InvalidPaymentStateException("Invoice INV-2026-0001 is already PAID."));

        assertThatThrownBy(() -> paymentService.processPayment(req))
                .isInstanceOf(InvalidPaymentStateException.class)
                .hasMessageContaining("already PAID");

        verify(paymentRepository, never()).save(any());
        verify(notificationClient, never()).createNotification(any());
    }

    @Test
    @DisplayName("processPayment: supports all three payment methods — UPI, CREDIT_CARD, DEBIT_CARD — when amount ≤ card limit")
    void processPayment_allPaymentMethods() {
        for (PaymentMethod method : PaymentMethod.values()) {
            // 45.00 is well within the ₹40,000 card limit for CREDIT/DEBIT
            PaymentRequest req = buildRequest(1L, method, "45.00");
            when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
            when(billingService.markPaid(1L)).thenReturn(pendingInvoice(1L, 10L));
            when(txnIdGen.next()).thenReturn("TXN-00000");
            when(paymentRepository.save(any(Payment.class))).thenAnswer(i -> i.getArgument(0));

            PaymentResponse res = paymentService.processPayment(req);
            assertThat(res.getPaymentMethod()).isEqualTo(method);
            assertThat(res.getPaymentStatus()).isEqualTo(PaymentStatus.PAID);

            reset(billingService, paymentRepository, txnIdGen,
                  notificationClient, patientClient, orderClient, lpsClient);
        }
    }

    // ── getPaymentsByInvoice ──────────────────────────────────────────────────

    @Test
    @DisplayName("getPaymentsByInvoice: returns list of payment responses for a valid invoice")
    void getPaymentsByInvoice_returnsList() {
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        Payment p = Payment.builder()
                .id(1L).invoiceId(1L).amountPaid(new BigDecimal("45.00"))
                .paymentMethod(PaymentMethod.UPI).transactionId("TXN-20260401-0001")
                .status(PaymentStatus.PAID).paidAt(LocalDateTime.now()).build();
        when(paymentRepository.findByInvoiceId(1L)).thenReturn(List.of(p));

        List<PaymentResponse> responses = paymentService.getPaymentsByInvoice(1L);

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getTransactionId()).isEqualTo("TXN-20260401-0001");
        assertThat(responses.get(0).getPaymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(responses.get(0).getPaymentMethod()).isEqualTo(PaymentMethod.UPI);
    }

    @Test
    @DisplayName("getPaymentsByInvoice: returns empty list when no payments made yet")
    void getPaymentsByInvoice_empty() {
        when(billingService.findInvoiceOrThrow(1L)).thenReturn(pendingInvoice(1L, 10L));
        when(paymentRepository.findByInvoiceId(1L)).thenReturn(List.of());

        assertThat(paymentService.getPaymentsByInvoice(1L)).isEmpty();
    }
}

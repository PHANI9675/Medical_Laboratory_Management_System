package com.medlab.billing.service;

import com.medlab.billing.client.InventoryClient;
import com.medlab.billing.client.NotificationClient;
import com.medlab.billing.client.OrderClient;
import com.medlab.billing.client.PatientClient;
import com.medlab.billing.dto.InvoiceResponse;
import com.medlab.billing.dto.NotificationSendRequest;
import com.medlab.billing.dto.OrderDetailResponse;
import com.medlab.billing.dto.PatientResponse;
import com.medlab.billing.exception.InvoiceAlreadyExistsException;
import com.medlab.billing.exception.InvalidPaymentStateException;
import com.medlab.billing.exception.ResourceNotFoundException;
import com.medlab.billing.model.Invoice;
import com.medlab.billing.model.InvoiceStatus;
import com.medlab.billing.repository.InvoiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class BillingService {

    private final InvoiceRepository invoiceRepository;
    private final InventoryClient inventoryClient;
    private final PatientClient patientClient;
    private final NotificationClient notificationClient;
    private final OrderClient orderClient;
    private final InvoiceNumberGenerator invoiceNumberGen;

    // ── Generate Invoice ─────────────────────────────────────

    /**
     * Triggered by Lab Processing Service when a result is approved.
     *
     * Pull model — orderId (path param) is the only input:
     *   1. Fetches { patientId, testIds } from Order Service  GET /orders/{orderId}/detail
     *   2. Fetches test prices from Inventory Service         GET /tests/{id}  (per testId)
     *   3. Fetches patient username from Patient Service      GET /patient/by-id/{patientId}
     *   4. Sends INVOICE_GENERATED notification via Notification Service  POST /notification
     */
    @Transactional
    public InvoiceResponse generateInvoice(Long orderId) {

        // Idempotency guard — one invoice per order
        invoiceRepository.findByOrderId(orderId).ifPresent(existing -> {
            throw new InvoiceAlreadyExistsException(
                    "Invoice already exists for orderId=" + orderId
                            + " → " + existing.getInvoiceNumber());
        });

        // INTEGRATION 1: fetch order details (patientId + testIds) from Order Service
        // Billing does NOT receive these in the request body — it fetches them itself.
        OrderDetailResponse orderDetail = orderClient.getOrderDetailById(orderId);
        if (orderDetail == null) {
            throw new IllegalStateException(
                    "Cannot generate invoice: Order Service unavailable for orderId=" + orderId);
        }

        // INTEGRATION 2: fetch test prices from Inventory Service
        BigDecimal total = calculateTotal(orderDetail.getTestIds());

        Invoice invoice = Invoice.builder()
                .invoiceNumber(invoiceNumberGen.next())
                .orderId(orderId)
                .patientId(orderDetail.getPatientId())
                .amount(total)
                .currency("INR")
                .status(InvoiceStatus.PENDING)
                .dueDate(LocalDate.now().plusDays(10))
                .build();

        invoice = invoiceRepository.save(invoice);
        log.info("Invoice generated: {} | orderId={} | amount={}",
                invoice.getInvoiceNumber(), orderId, total);

        // INTEGRATION 3+4: resolve patientId → username (Patient Service),
        //                   then send INVOICE_GENERATED (Notification Service)
        notifyPatient(orderDetail.getPatientId(),
                "Your test result has been approved. Invoice "
                        + invoice.getInvoiceNumber() + " of ₹" + total
                        + " has been generated. Due date: " + invoice.getDueDate(),
                "INVOICE_GENERATED");

        return toResponse(invoice);
    }

    // ── Read operations ──────────────────────────────────────

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceById(Long id) {
        return toResponse(findInvoiceOrThrow(id));
    }

    @Transactional(readOnly = true)
    public InvoiceResponse getInvoiceByOrderId(Long orderId) {
        Invoice inv = invoiceRepository.findByOrderId(orderId)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Invoice", "orderId", orderId));
        return toResponse(inv);
    }

    @Transactional(readOnly = true)
    public List<InvoiceResponse> getInvoicesByPatient(Long patientId) {
        return invoiceRepository.findByPatientId(patientId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── State transition — called by PaymentService ──────────

    @Transactional
    public Invoice markPaid(Long invoiceId) {
        Invoice inv = findInvoiceOrThrow(invoiceId);

        if (inv.getStatus() == InvoiceStatus.PAID) {
            throw new InvalidPaymentStateException(
                    "Invoice " + inv.getInvoiceNumber() + " is already PAID.");
        }
        if (inv.getStatus() == InvoiceStatus.CANCELLED) {
            throw new InvalidPaymentStateException(
                    "Cannot pay a CANCELLED invoice: " + inv.getInvoiceNumber());
        }

        inv.setStatus(InvoiceStatus.PAID);
        return invoiceRepository.save(inv);
    }

    // ── Package-visible helper used by PaymentService ────────

    Invoice findInvoiceOrThrow(Long id) {
        return invoiceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Invoice", "id", id));
    }

    // ── Private helpers ──────────────────────────────────────

    private BigDecimal calculateTotal(List<Long> testIds) {
        if (testIds == null || testIds.isEmpty()) {
            log.warn("No testIds in order — invoice total defaults to 0.00");
            return BigDecimal.ZERO;
        }
        BigDecimal total = BigDecimal.ZERO;
        for (Long testId : testIds) {
            try {
                // INTEGRATION: fetch test price from Inventory Service
                Map<String, Object> test = inventoryClient.getTestById(testId);
                Object priceObj = test.get("price");
                if (priceObj != null) {
                    total = total.add(new BigDecimal(priceObj.toString()));
                    log.debug("testId={} price={}", testId, priceObj);
                } else {
                    log.warn("No price found for testId={} — skipping", testId);
                }
            } catch (Exception ex) {
                log.warn("Could not fetch price for testId={} (InventoryService unavailable): {}",
                        testId, ex.getMessage());
            }
        }
        return total;
    }

    private void notifyPatient(Long patientId, String message, String type) {
        try {
            // INTEGRATION: resolve patientId → username via Patient Service,
            // then send to Notification Service (which identifies users by username).
            PatientResponse patient = patientClient.getPatientById(patientId);
            if (patient == null || patient.getUsername() == null) {
                log.warn("Cannot send notification — username not resolved for patientId={}", patientId);
                return;
            }
            notificationClient.createNotification(
                    new NotificationSendRequest(patient.getUsername(), message, type)
            );
        } catch (Exception ex) {
            log.error("Notification failed for patientId={}: {}", patientId, ex.getMessage());
        }
    }

    private InvoiceResponse toResponse(Invoice inv) {
        return InvoiceResponse.builder()
                .id(inv.getId())
                .invoiceNumber(inv.getInvoiceNumber())
                .orderId(inv.getOrderId())
                .patientId(inv.getPatientId())
                .amount(inv.getAmount())
                .currency(inv.getCurrency())
                .status(inv.getStatus())
                .dueDate(inv.getDueDate())
                .createdAt(inv.getCreatedAt())
                .build();
    }
}

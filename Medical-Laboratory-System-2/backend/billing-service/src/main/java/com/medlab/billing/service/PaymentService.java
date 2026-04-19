package com.medlab.billing.service;

import com.medlab.billing.client.LpsClient;
import com.medlab.billing.client.NotificationClient;
import com.medlab.billing.client.OrderClient;
import com.medlab.billing.client.PatientClient;
import com.medlab.billing.dto.LpsResultResponse;
import com.medlab.billing.dto.NotificationSendRequest;
import com.medlab.billing.dto.OrderDetailResponse;
import com.medlab.billing.dto.PatientResponse;
import com.medlab.billing.exception.InvalidPaymentAmountException;
import com.medlab.billing.exception.InvalidPaymentStateException;
import com.medlab.billing.model.PaymentMethod;
import com.medlab.billing.dto.PaymentRequest;
import com.medlab.billing.dto.PaymentResponse;
import com.medlab.billing.model.Invoice;
import com.medlab.billing.model.Payment;
import com.medlab.billing.model.PaymentStatus;
import com.medlab.billing.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    /**
     * Maximum single-transaction limit for CREDIT_CARD and DEBIT_CARD.
     * Transactions exceeding this cap are declined to prevent overspending.
     */
    private static final BigDecimal CARD_TRANSACTION_LIMIT = new BigDecimal("40000.00");

    private final PaymentRepository paymentRepository;
    private final BillingService billingService;
    private final PatientClient patientClient;
    private final NotificationClient notificationClient;
    private final OrderClient orderClient;
    private final LpsClient lpsClient;
    private final TransactionIdGenerator txnIdGen;

    // ── Process Payment (simulated gateway) ─────────────────

    @Transactional
    public PaymentResponse processPayment(PaymentRequest req) {

        // 1. Load invoice — throws 404 if not found
        Invoice invoice = billingService.findInvoiceOrThrow(req.getInvoiceId());

        // 2. Amount mismatch guard — patient must pay the exact invoice amount.
        //    Overpaying or underpaying is rejected to prevent accounting errors.
        if (req.getAmount().compareTo(invoice.getAmount()) != 0) {
            throw new InvalidPaymentAmountException(
                    "Payment amount \u20b9" + req.getAmount() +
                    " does not match invoice amount \u20b9" + invoice.getAmount() +
                    ". Please pay the exact invoice amount.");
        }

        // 3. Card transaction limit guard — CREDIT_CARD and DEBIT_CARD are capped
        //    at ₹40,000 per transaction to prevent overspending.
        if ((req.getPaymentMethod() == PaymentMethod.CREDIT_CARD ||
             req.getPaymentMethod() == PaymentMethod.DEBIT_CARD) &&
             req.getAmount().compareTo(CARD_TRANSACTION_LIMIT) > 0) {
            throw new InvalidPaymentStateException(
                    req.getPaymentMethod() + " transaction declined: amount \u20b9" + req.getAmount() +
                    " exceeds the card transaction limit of \u20b9" + CARD_TRANSACTION_LIMIT +
                    ". Please use UPI or split the payment.");
        }

        // 4. State guard — throws InvalidPaymentStateException if already PAID/CANCELLED
        billingService.markPaid(req.getInvoiceId());

        // 5. Generate simulated transaction ID
        String txnId = txnIdGen.next();
        LocalDateTime now = LocalDateTime.now();

        // 6. Persist payment record (simulated gateway — always PAID when guards pass)
        Payment payment = Payment.builder()
                .invoiceId(req.getInvoiceId())
                .amountPaid(req.getAmount())
                .paymentMethod(req.getPaymentMethod())
                .transactionId(txnId)
                .status(PaymentStatus.PAID)
                .paidAt(now)
                .build();
        paymentRepository.save(payment);

        log.info("Payment recorded: txn={} | invoice={} | method={} | amount={}",
                txnId, req.getInvoiceId(), req.getPaymentMethod(), req.getAmount());

        // 7. Notify patient — failure must NOT block payment response
        notifyPaymentSuccess(invoice, payment);

        return PaymentResponse.builder()
                .transactionId(txnId)
                .invoiceId(req.getInvoiceId())
                .amount(req.getAmount())
                .paymentMethod(req.getPaymentMethod())
                .paymentStatus(PaymentStatus.PAID)
                .paidAt(now)
                .message("Payment successful. Transaction ID: " + txnId)
                .build();
    }

    // ── Payment History ──────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PaymentResponse> getPaymentsByInvoice(Long invoiceId) {
        billingService.findInvoiceOrThrow(invoiceId); // 404 guard
        return paymentRepository.findByInvoiceId(invoiceId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    // ── Private helpers ──────────────────────────────────────

    private void notifyPaymentSuccess(Invoice invoice, Payment payment) {
        // ── Resolve username ──────────────────────────────────
        // Primary:  Patient Service GET /patient/by-id/{id}  (ADMIN/LAB_TECH JWT)
        // Fallback: SecurityContext (PATIENT JWT → 403 from patient-service → use principal)
        String username = null;
        try {
            try {
                PatientResponse patient = patientClient.getPatientById(invoice.getPatientId());
                if (patient != null && patient.getUsername() != null) {
                    username = patient.getUsername();
                }
            } catch (Exception feignEx) {
                log.debug("PatientClient call failed for patientId={}: {} — trying SecurityContext fallback",
                        invoice.getPatientId(), feignEx.getMessage());
            }

            if (username == null) {
                Authentication auth = SecurityContextHolder.getContext().getAuthentication();
                if (auth != null && auth.isAuthenticated()
                        && !"anonymousUser".equals(auth.getName())) {
                    username = auth.getName();
                    log.info("PatientClient unavailable for patientId={} — using SecurityContext username '{}' for payment notification",
                            invoice.getPatientId(), username);
                }
            }

            if (username == null) {
                log.warn("Cannot send payment notification — username not resolved for patientId={}",
                        invoice.getPatientId());
                return;
            }

            // ── 1. PAYMENT_SUCCESS notification ──────────────
            String paymentMsg = String.format(
                    "Payment of \u20b9%s via %s was successful. Transaction ID: %s.",
                    payment.getAmountPaid(), payment.getPaymentMethod(), payment.getTransactionId()
            );
            notificationClient.createNotification(
                    new NotificationSendRequest(username, paymentMsg, "PAYMENT_SUCCESS")
            );
            log.info("PAYMENT_SUCCESS notification sent: txn={} username={}", payment.getTransactionId(), username);

            // ── 2. LAB_RESULT notification ────────────────────
            // Fetch sampleId from Order Service, then fetch approved result from LPS.
            // Any failure here is caught and logged — payment is already confirmed.
            sendLabResultNotification(invoice, username);

        } catch (Exception ex) {
            log.error("Notification failed after payment txn={}: {}",
                    payment.getTransactionId(), ex.getMessage());
        }
    }

    /**
     * Fetches the approved lab result from LPS and sends it to the patient
     * as a LAB_RESULT notification.
     *
     * Feign calls made (in order):
     *   1. OrderClient  GET /orders/{orderId}/detail  — to get sampleId
     *   2. LpsClient    GET /api/jobs/results/by-sample/{sampleId}  — to get result
     *   3. NotificationClient  POST /notification — deliver LAB_RESULT message
     *
     * All failures are caught so payment confirmation is never affected.
     */
    private void sendLabResultNotification(Invoice invoice, String username) {
        try {
            // Step 1: get sampleId from Order Service
            OrderDetailResponse orderDetail = orderClient.getOrderDetailById(invoice.getOrderId());
            if (orderDetail == null || orderDetail.getSampleId() == null) {
                log.warn("LAB_RESULT notification skipped — sampleId not available for orderId={}",
                        invoice.getOrderId());
                return;
            }
            Long sampleId = orderDetail.getSampleId();

            // Step 2: fetch approved result from LPS
            LpsResultResponse lpsResult = lpsClient.getResultBySampleId(sampleId);
            if (lpsResult == null || lpsResult.getResult() == null) {
                log.warn("LAB_RESULT notification skipped — no result returned from LPS for sampleId={}",
                        sampleId);
                return;
            }

            // Step 3: format and send LAB_RESULT notification
            String resultMsg = String.format(
                    "Your lab test result is ready! Test #%d result: %s  " +
                    "Please consult your doctor for interpretation.",
                    lpsResult.getTestId(), lpsResult.getResult()
            );
            notificationClient.createNotification(
                    new NotificationSendRequest(username, resultMsg, "LAB_RESULT")
            );
            log.info("LAB_RESULT notification sent: sampleId={} username={}", sampleId, username);

        } catch (Exception ex) {
            log.warn("LAB_RESULT notification failed for orderId={}: {}",
                    invoice.getOrderId(), ex.getMessage());
        }
    }

    private PaymentResponse toResponse(Payment p) {
        return PaymentResponse.builder()
                .transactionId(p.getTransactionId())
                .invoiceId(p.getInvoiceId())
                .amount(p.getAmountPaid())
                .paymentMethod(p.getPaymentMethod())
                .paymentStatus(p.getStatus())
                .paidAt(p.getPaidAt())
                .build();
    }
}
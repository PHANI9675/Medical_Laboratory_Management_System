package com.medlab.order_service.service;

import com.medlab.order_service.client.InventoryClient;
import com.medlab.order_service.client.LpsClient;
import com.medlab.order_service.client.NotificationClient;
import com.medlab.order_service.client.PatientClient;
import com.medlab.order_service.dto.CreateJobRequest;
import com.medlab.order_service.dto.CreateOrderRequest;
import com.medlab.order_service.dto.NotificationRequest;
import com.medlab.order_service.dto.OrderDetailResponse;
import com.medlab.order_service.dto.OrderResponse;
import com.medlab.order_service.dto.PatientResponse;
import com.medlab.order_service.entity.Order;
import com.medlab.order_service.entity.OrderTest;
import com.medlab.order_service.entity.Sample;
import com.medlab.order_service.enums.OrderPriority;
import com.medlab.order_service.enums.OrderStatus;
import com.medlab.order_service.exception.OrderNotFoundException;
import com.medlab.order_service.repository.OrderRepository;
import com.medlab.order_service.repository.SampleRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final SampleRepository sampleRepository;
    private final LpsClient lpsClient;
    private final PatientClient patientClient;
    private final NotificationClient notificationClient;
    private final InventoryClient inventoryClient;

    public OrderService(OrderRepository orderRepository,
                        SampleRepository sampleRepository,
                        LpsClient lpsClient,
                        PatientClient patientClient,
                        NotificationClient notificationClient,
                        InventoryClient inventoryClient) {
        this.orderRepository = orderRepository;
        this.sampleRepository = sampleRepository;
        this.lpsClient = lpsClient;
        this.patientClient = patientClient;
        this.notificationClient = notificationClient;
        this.inventoryClient = inventoryClient;
    }

    // ── Create Order ─────────────────────────────────────────

    public OrderResponse createOrder(CreateOrderRequest request) {
        // INTEGRATION: fetch patientId from Patient Service using the caller's JWT.
        // patientId is NOT sent in the request body — Order Service resolves it here.
        // Patient Service reads the username from the forwarded JWT and returns the profile.
        PatientResponse patient = patientClient.getMyProfile();
        if (patient == null || patient.getId() == null) {
            throw new IllegalStateException(
                    "Cannot place order: your patient profile was not found. " +
                    "Please create your profile first via POST /patient/addProfile.");
        }
        Long patientId = patient.getId();
        log.info("Order placement: resolved patientId={} username={} from Patient Service",
                patientId, patient.getUsername());

        Order order = new Order();
        order.setOrderNumber("ORD-" + System.currentTimeMillis());
        order.setPatientId(patientId);          // fetched from Patient Service, NOT from request body
        order.setRequestedBy(request.getRequestedBy());
        order.setStatus(OrderStatus.CREATED);
        order.setPriority(
                request.getPriority() != null
                        ? request.getPriority()
                        : OrderPriority.ROUTINE
        );
        order.setCreatedAt(LocalDateTime.now());

        request.getTests().forEach(testId -> {
            OrderTest ot = new OrderTest();
            ot.setTestId(testId);
            ot.setOrder(order);
            order.getOrderTests().add(ot);
        });

        Order savedOrder = orderRepository.save(order);

        // INTEGRATION: notify patient that the order has been placed.
        //   We already have the username from getMyProfile() above — pass it directly
        //   so we don't need a second PatientClient call in the notification helper.
        //   Step 1 — fetch estimated total from Inventory Service  GET /tests/{testId}
        //   Step 2 — send ORDER_PLACED notification via Notification Service  POST /notification
        notifyOrderPlaced(savedOrder, request.getTests(), patient.getUsername());

        return new OrderResponse(
                savedOrder.getId(),
                savedOrder.getOrderNumber(),
                savedOrder.getStatus().name(),
                savedOrder.getPriority().name()
        );
    }

    // ── Get Order by DB ID ────────────────────────────────────

    public Order getOrderById(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    // ── Get Order detail by DB ID — called by Billing Service ─
    // Returns { orderId, patientId, testIds } so Billing can:
    //   - look up test prices in Inventory Service
    //   - look up patient username in Patient Service for notification

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderDetailById(Long orderId) {
        Order order = getOrderById(orderId);
        List<Long> testIds = order.getOrderTests()
                .stream()
                .map(OrderTest::getTestId)
                .collect(Collectors.toList());
        // Include sampleId so Billing Service can call LPS to fetch the test result
        // and deliver it to the patient as a LAB_RESULT notification after payment.
        // sampleId is null when sample not yet collected (Billing won't send result then).
        Long sampleId = sampleRepository.findByOrder(order)
                .map(Sample::getId)
                .orElse(null);
        return new OrderDetailResponse(order.getId(), order.getPatientId(), testIds, sampleId);
    }

    // ── Get Order detail by Sample ID — called by LPS ─────────
    // Returns { orderId, patientId, testIds, sampleId } so LPS can:
    //   - pass orderId to Billing to trigger invoice generation

    @Transactional(readOnly = true)
    public OrderDetailResponse getOrderBySampleId(Long sampleId) {
        Sample sample = sampleRepository.findById(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("Sample not found: " + sampleId));
        Order order = sample.getOrder();
        List<Long> testIds = order.getOrderTests()
                .stream()
                .map(OrderTest::getTestId)
                .collect(Collectors.toList());
        return new OrderDetailResponse(order.getId(), order.getPatientId(), testIds, sampleId);
    }

    // ── Collect Sample ────────────────────────────────────────
    // Saves sample, updates order status, then auto-creates LPS jobs.
    // INTEGRATION: calls LPS POST /api/jobs (one per test) with { sampleId, testId }.

    @Transactional
    public void collectSample(Long orderId, Long collectedBy) {
        Order order = getOrderById(orderId);

        Sample sample = new Sample();
        sample.setOrder(order);
        sample.setCollectedBy(collectedBy);
        sample.setCollectedAt(LocalDateTime.now());

        sampleRepository.save(sample);

        order.setStatus(OrderStatus.SAMPLE_COLLECTED);
        orderRepository.save(order);

        Long sampleId = sample.getId();
        for (OrderTest ot : order.getOrderTests()) {
            try {
                lpsClient.createJob(new CreateJobRequest(sampleId, ot.getTestId()));
            } catch (Exception e) {
                log.error("Failed to create LPS job for sampleId={} testId={}: {}",
                        sampleId, ot.getTestId(), e.getMessage());
            }
        }
    }

    // ── Cancel Order ──────────────────────────────────────────

    public List<Order> getAllOrders() {
        return orderRepository.findAll();
    }

    public void cancelOrder(Long orderId) {
        Order order = getOrderById(orderId);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new IllegalStateException("Order " + orderId + " is already cancelled");
        }
        order.setStatus(OrderStatus.CANCELLED);
        orderRepository.save(order);

        // INTEGRATION: notify patient that their order has been cancelled.
        //   Step 1 — resolve patientId → username via Patient Service (fallback: SecurityContext)
        //   Step 2 — send ORDER_CANCELLED notification via Notification Service
        notifyOrderCancelled(order);
    }

    // ── Private — notification helpers ───────────────────────

    /**
     * Sends an ORDER_PLACED notification to the patient.
     *
     * username is passed in directly — it was already resolved by getMyProfile()
     * in createOrder(), so no second PatientClient call is needed here.
     *
     * Feign calls made (in order):
     *  1. InventoryClient    GET /tests/{testId}   — price lookup per test (for estimated total)
     *  2. NotificationClient POST /notification    — deliver ORDER_PLACED message
     *
     * All failures are caught; they must never block order creation.
     */
    private void notifyOrderPlaced(Order order, List<Long> testIds, String username) {
        try {
            if (username == null) {
                log.warn("ORDER_PLACED notification skipped — no username for orderId={}", order.getId());
                return;
            }

            // INTEGRATION 1: estimated total from Inventory Service
            BigDecimal estimatedTotal = computeEstimatedTotal(testIds);

            String msg;
            if (estimatedTotal.compareTo(BigDecimal.ZERO) > 0) {
                msg = String.format(
                        "Your order %s has been placed successfully. Estimated total: \u20b9%s. " +
                        "You will be notified once your lab results are ready.",
                        order.getOrderNumber(), estimatedTotal.toPlainString());
            } else {
                msg = String.format(
                        "Your order %s has been placed successfully. " +
                        "You will be notified once your lab results are ready.",
                        order.getOrderNumber());
            }

            // INTEGRATION 2: send notification
            notificationClient.createNotification(
                    new NotificationRequest(username, msg, "ORDER_PLACED"));
            log.info("ORDER_PLACED notification sent: orderId={} username={}", order.getId(), username);

        } catch (Exception ex) {
            log.error("Failed to send ORDER_PLACED notification for orderId={}: {}",
                    order.getId(), ex.getMessage());
        }
    }

    /**
     * Sends an ORDER_CANCELLED notification to the patient.
     *
     * Feign calls made (in order):
     *  1. PatientClient     GET /patient/by-id/{id}     — resolve username (with SecurityContext fallback)
     *  2. NotificationClient  POST /notification         — deliver cancellation message
     */
    private void notifyOrderCancelled(Order order) {
        try {
            String username = resolvePatientUsername(order.getPatientId());
            if (username == null) {
                log.warn("ORDER_CANCELLED notification skipped — username not resolved for patientId={}",
                        order.getPatientId());
                return;
            }

            String msg = String.format(
                    "Your order %s has been cancelled. " +
                    "If this was unexpected, please contact our support team.",
                    order.getOrderNumber());

            notificationClient.createNotification(
                    new NotificationRequest(username, msg, "ORDER_CANCELLED"));
            log.info("ORDER_CANCELLED notification sent: orderId={} username={}", order.getId(), username);

        } catch (Exception ex) {
            log.error("Failed to send ORDER_CANCELLED notification for orderId={}: {}",
                    order.getId(), ex.getMessage());
        }
    }

    /**
     * Resolves a patientId to a username for notification routing.
     *
     * Primary:  Patient Service GET /patient/by-id/{id}
     *           Works when the forwarded JWT has ADMIN or LAB_TECH authority.
     *
     * Fallback: SecurityContextHolder.getContext().getAuthentication().getName()
     *           Triggers when PATIENT places/cancels their own order — the forwarded
     *           PATIENT JWT causes patient-service to return 403, Feign throws, we
     *           catch it and use the SecurityContext (principal IS the patient).
     */
    private String resolvePatientUsername(Long patientId) {
        // Primary: Patient Service (ADMIN/LAB_TECH JWT succeeds)
        try {
            PatientResponse patient = patientClient.getPatientById(patientId);
            if (patient != null && patient.getUsername() != null) {
                return patient.getUsername();
            }
        } catch (Exception ex) {
            log.debug("PatientClient call failed for patientId={}: {} — falling back to SecurityContext",
                    patientId, ex.getMessage());
        }

        // Fallback: SecurityContext (principal IS the patient when they self-serve)
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()
                && !"anonymousUser".equals(auth.getName())) {
            log.info("PatientClient unavailable for patientId={} — using SecurityContext username '{}'",
                    patientId, auth.getName());
            return auth.getName();
        }

        return null;
    }

    /**
     * Fetches each test's price from Inventory Service and returns the total.
     * Returns ZERO if Inventory is unavailable — notification will be sent
     * without the amount rather than failing.
     *
     * INTEGRATION: calls GET /tests/{testId} on Inventory Service per test.
     */
    private BigDecimal computeEstimatedTotal(List<Long> testIds) {
        BigDecimal total = BigDecimal.ZERO;
        if (testIds == null || testIds.isEmpty()) return total;

        for (Long testId : testIds) {
            try {
                Map<String, Object> test = inventoryClient.getTestById(testId);
                if (test != null && test.get("price") != null) {
                    total = total.add(new BigDecimal(test.get("price").toString()));
                    log.debug("Price fetched for testId={}: {}", testId, test.get("price"));
                }
            } catch (Exception ex) {
                log.warn("Cannot fetch price for testId={}: {}", testId, ex.getMessage());
            }
        }
        return total;
    }
}

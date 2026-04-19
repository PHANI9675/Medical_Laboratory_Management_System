package com.medlab.order_service.controller;

import com.medlab.order_service.dto.CreateOrderRequest;
import com.medlab.order_service.dto.OrderDetailResponse;
import com.medlab.order_service.dto.OrderResponse;
import com.medlab.order_service.dto.CancelledOrderResponse;
import com.medlab.order_service.entity.Order;
import com.medlab.order_service.enums.OrderStatus;
import com.medlab.order_service.service.OrderService;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping("/addOrder")
    @PreAuthorize("hasAnyAuthority('PATIENT','LAB_TECH')")
    public ResponseEntity<OrderResponse> addOrder(@RequestBody CreateOrderRequest request) {
        OrderResponse response = orderService.createOrder(request);
        return new ResponseEntity<>(response, HttpStatus.CREATED);
    }

    @GetMapping("/viewOrder/{id}")
    @PreAuthorize("hasAnyAuthority('PATIENT','LAB_TECH')")
    public ResponseEntity<?> viewOrder(@PathVariable Long id) {
        Order order = orderService.getOrderById(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            return ResponseEntity.ok(
                    new CancelledOrderResponse(
                            order.getId(),
                            order.getOrderNumber(),
                            order.getStatus().name(),
                            "This order has been cancelled"
                    )
            );
        }
        return ResponseEntity.ok(order);
    }

    @PostMapping("/collectSample/{id}")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<String> collectSample(
            @PathVariable Long id,
            @RequestParam Long collectedBy) {
        orderService.collectSample(id, collectedBy);
        return ResponseEntity.ok("Sample collected successfully");
    }

    @PostMapping("/cancelOrder/{id}")
    @PreAuthorize("hasAnyAuthority('PATIENT','LAB_TECH')")
    public ResponseEntity<String> cancelOrder(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return ResponseEntity.ok("Order cancelled successfully");
    }

    /**
     * Internal endpoint — called by LPS to resolve sampleId → orderId + patientId + testIds.
     * Restricted to ADMIN and LAB_TECH (never called directly by PATIENT).
     */
    @GetMapping("/by-sample/{sampleId}")
    @PreAuthorize("hasAnyAuthority('ADMIN','LAB_TECH')")
    public ResponseEntity<OrderDetailResponse> getOrderBySampleId(@PathVariable Long sampleId) {
        return ResponseEntity.ok(orderService.getOrderBySampleId(sampleId));
    }

    /**
     * Internal endpoint — called by Billing Service to fetch order details using orderId.
     *
     * Billing receives only the orderId (in the billing/generate/{orderId} path).
     * It calls this endpoint to get { patientId, testIds } for:
     *   - invoice calculation (testIds → Inventory Service for prices)
     *   - patient notification (patientId → Patient Service for username)
     *
     * Restricted to ADMIN and LAB_TECH (Billing calls with the forwarded JWT from LPS/ADMIN).
     */
    @GetMapping("/{orderId}/detail")
    @PreAuthorize("hasAnyAuthority('ADMIN','LAB_TECH','PATIENT')")
    public ResponseEntity<OrderDetailResponse> getOrderDetailById(@PathVariable Long orderId) {
        return ResponseEntity.ok(orderService.getOrderDetailById(orderId));
    }

    @GetMapping("/viewAllOrders")
    @PreAuthorize("hasAuthority('ADMIN')")
    public ResponseEntity<List<Order>> viewAllOrders() {
        return ResponseEntity.ok(orderService.getAllOrders());
    }
}

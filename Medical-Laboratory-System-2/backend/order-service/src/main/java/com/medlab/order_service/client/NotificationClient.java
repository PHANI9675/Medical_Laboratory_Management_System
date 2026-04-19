package com.medlab.order_service.client;

import com.medlab.order_service.dto.NotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Feign client to Notification Service (Port 8087).
 *
 * Called by OrderService to send notifications to patients:
 *   - ORDER_PLACED   : when an order is placed successfully
 *   - ORDER_CANCELLED: when an order is cancelled
 *
 * Fallback: NotificationClientFallback — logs silently so order operations
 * are never blocked by a notification failure.
 */
@FeignClient(name = "notification-service", fallback = NotificationClientFallback.class)
public interface NotificationClient {

    @PostMapping("/notification")
    void createNotification(@RequestBody NotificationRequest request);
}

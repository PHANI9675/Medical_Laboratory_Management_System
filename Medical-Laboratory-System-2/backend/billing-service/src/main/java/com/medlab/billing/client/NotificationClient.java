package com.medlab.billing.client;

import com.medlab.billing.dto.NotificationSendRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Calls Notification Service (Port 8087) to send in-app notifications.
 * Replaces the former UserClient which incorrectly targeted user-service (auth-service).
 *
 * Used by BillingService (invoice generated) and PaymentService (payment success).
 * Fallback: NotificationClientFallback — notification failure must NEVER block billing.
 */
@FeignClient(name = "notification-service", fallback = NotificationClientFallback.class)
public interface NotificationClient {

    @PostMapping("/notification")
    String createNotification(@RequestBody NotificationSendRequest request);
}

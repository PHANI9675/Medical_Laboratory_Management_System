package com.medlab.inventory.client;

import com.medlab.inventory.dto.LowStockNotificationRequest;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

/**
 * Calls notification-service (Port 8087) to send a low-stock alert to the admin.
 *
 * Triggered by InventoryService.adjustStock() whenever stock drops to or below
 * the item's configured lowStockThreshold.
 *
 * The notification goes to the admin username only — NOT to patients.
 * The admin's username is configurable via:
 *   inventory.notification.admin-username=admin@lab.com (in application.yaml)
 *
 * Fallback: NotificationClientFallback — a low-stock alert failure must NEVER
 * block or roll back a legitimate stock adjustment.
 */
@FeignClient(name = "notification-service", fallback = NotificationClientFallback.class)
public interface NotificationClient {

    @PostMapping("/notification")
    String sendNotification(@RequestBody LowStockNotificationRequest request);
}

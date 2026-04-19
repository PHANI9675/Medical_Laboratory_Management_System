package com.medlab.inventory.client;

import com.medlab.inventory.dto.LowStockNotificationRequest;
import org.springframework.stereotype.Component;

/**
 * Fallback for NotificationClient.
 *
 * If Notification_service is down or times out, this runs instead.
 * It logs the low-stock event to stdout so operators can still spot it
 * in application logs, and returns gracefully so the stock adjustment
 * response is NOT affected.
 */
@Component
public class NotificationClientFallback implements NotificationClient {

    @Override
    public String sendNotification(LowStockNotificationRequest request) {
        System.err.println("[FALLBACK] Notification_service unreachable — low-stock alert NOT sent. " +
                "Details: username=" + request.getUsername() +
                " | message=" + request.getMessage());
        return "FALLBACK: notification not sent";
    }
}

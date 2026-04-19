package com.medlab.order_service.client;

import com.medlab.order_service.dto.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when Notification Service is unreachable.
 * Logs a warning — notification failure must NOT block order creation or cancellation.
 */
@Component
@Slf4j
public class NotificationClientFallback implements NotificationClient {

    @Override
    public void createNotification(NotificationRequest request) {
        log.warn("NotificationService unavailable — notification NOT sent: type={} username={}",
                request.getType(), request.getUsername());
    }
}

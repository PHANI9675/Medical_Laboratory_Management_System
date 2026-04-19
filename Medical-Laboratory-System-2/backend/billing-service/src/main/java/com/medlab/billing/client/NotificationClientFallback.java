package com.medlab.billing.client;

import com.medlab.billing.dto.NotificationSendRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Fallback when Notification Service is unreachable.
 * Logs a warning — notification failure must NEVER block billing or payment.
 */
@Component
@Slf4j
public class NotificationClientFallback implements NotificationClient {

    @Override
    public String createNotification(NotificationSendRequest request) {
        log.warn("NotificationService unavailable — notification NOT sent: username={}, type={}",
                request.getUsername(), request.getType());
        return "Notification skipped (service unavailable)";
    }
}

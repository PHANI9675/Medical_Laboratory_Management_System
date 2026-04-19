package com.medlab.order_service.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request body sent to Notification Service POST /notification.
 * Matches the NotificationSendRequest expected by notification-service.
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class NotificationRequest {
    private String username;
    private String message;
    private String type;
}

package com.medlab.inventory.dto;

/**
 * Request body for Notification_service POST /notification.
 *
 * Matches Notification_service's NotificationRequest contract:
 *   { username, message, type }
 *
 * username: the admin's username (configured in application.yaml as
 *           inventory.notification.admin-username)
 * message:  human-readable low-stock description
 * type:     always "LOW_STOCK_ALERT" for inventory-triggered notifications
 */
public class LowStockNotificationRequest {

    private String username;
    private String message;
    private String type;

    public LowStockNotificationRequest(String username, String message, String type) {
        this.username = username;
        this.message = message;
        this.type = type;
    }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }
}

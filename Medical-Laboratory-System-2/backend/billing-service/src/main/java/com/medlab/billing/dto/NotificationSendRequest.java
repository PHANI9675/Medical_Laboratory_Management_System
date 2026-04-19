package com.medlab.billing.dto;

/**
 * Payload sent to Notification Service POST /notification.
 * Uses username (not userId) to match Notification Service's expected contract.
 */
public class NotificationSendRequest {

    private String username;
    private String message;
    private String type;

    public NotificationSendRequest() {}

    public NotificationSendRequest(String username, String message, String type) {
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

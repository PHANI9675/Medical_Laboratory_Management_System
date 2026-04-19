package com.medlab.billing.dto;

import lombok.Builder;
import lombok.Data;

/**
 * Payload sent to User Service (P1) POST /notifications
 * to persist an in-app notification for a patient.
 *
 * type values: INVOICE_GENERATED | PAYMENT_SUCCESS
 */
@Data
@Builder
public class NotificationRequest {
    private Long userId;
    private String message;
    private String type;
}
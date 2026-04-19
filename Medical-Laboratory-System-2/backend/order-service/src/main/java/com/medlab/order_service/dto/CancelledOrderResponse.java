package com.medlab.order_service.dto;

public class CancelledOrderResponse {

    private Long orderId;
    private String orderNumber;
    private String status;
    private String message;

    public CancelledOrderResponse(
            Long orderId,
            String orderNumber,
            String status,
            String message
    ) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.message = message;
    }

    public Long getOrderId() {
        return orderId;
    }

    public String getOrderNumber() {
        return orderNumber;
    }

    public String getStatus() {
        return status;
    }

    public String getMessage() {
        return message;
    }
}
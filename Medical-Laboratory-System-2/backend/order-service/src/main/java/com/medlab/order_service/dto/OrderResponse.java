package com.medlab.order_service.dto;

public class OrderResponse {

    private Long orderId;
    private String orderNumber;
    private String status;
    private String priority;

    public OrderResponse(Long orderId, String orderNumber,
                          String status, String priority) {
        this.orderId = orderId;
        this.orderNumber = orderNumber;
        this.status = status;
        this.priority = priority;
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

    public String getPriority() {
        return priority;
    }
}
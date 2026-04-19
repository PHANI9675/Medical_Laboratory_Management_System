package com.medlab.order_service.dto;

import java.util.List;
import com.medlab.order_service.enums.OrderPriority;


public class CreateOrderRequest {

    // patientId is intentionally NOT here — Order Service fetches it from
    // Patient Service (GET /patient/profile) using the caller's JWT.
    // Only the minimum required fields are sent by the client.
    private Long requestedBy;
    private List<Long> tests;
    private OrderPriority priority;

    public Long getRequestedBy() {
        return requestedBy;
    }

    public void setRequestedBy(Long requestedBy) {
        this.requestedBy = requestedBy;
    }

    public List<Long> getTests() {
        return tests;
    }

    public void setTests(List<Long> tests) {
        this.tests = tests;
    }

    public OrderPriority getPriority() {
        return priority;
    }

    public void setPriority(OrderPriority priority) {
        this.priority = priority;
    }

}

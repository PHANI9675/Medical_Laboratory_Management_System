package com.medlab.billing.dto;

import com.medlab.billing.model.PaymentMethod;
import com.medlab.billing.model.PaymentStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
public class PaymentResponse {
    private String transactionId;
    private Long invoiceId;
    private BigDecimal amount;
    private PaymentMethod paymentMethod;
    private PaymentStatus paymentStatus;   // Always PAID (mocked)
    private LocalDateTime paidAt;
    private String message;
}
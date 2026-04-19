package com.medlab.billing.dto;

import com.medlab.billing.model.PaymentMethod;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.math.BigDecimal;

@Data
public class PaymentRequest {

    @NotNull(message = "invoiceId is required")
    private Long invoiceId;

    @NotNull(message = "paymentMethod is required")
    private PaymentMethod paymentMethod;   // CREDIT_CARD | DEBIT_CARD | UPI

    @NotNull(message = "amount is required")
    @DecimalMin(value = "0.01", message = "amount must be positive")
    private BigDecimal amount;
}
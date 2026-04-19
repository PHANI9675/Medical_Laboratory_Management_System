package com.medlab.billing.dto;

import com.medlab.billing.model.InvoiceStatus;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
@Builder
public class InvoiceResponse {
    private Long id;
    private String invoiceNumber;
    private Long orderId;
    private Long patientId;
    private BigDecimal amount;
    private String currency;
    private InvoiceStatus status;
    private LocalDate dueDate;
    private LocalDateTime createdAt;
}
package com.medlab.inventory.dto;

import jakarta.validation.constraints.*;
import lombok.Data;

@Data
public class AdjustInventoryRequest {

    @NotNull(message = "Item ID is required")
    private Long itemId;

    // positive = add stock, negative = reduce stock
    @NotNull(message = "Quantity change is required")
    private Integer quantityChange;

    private String reason;
}
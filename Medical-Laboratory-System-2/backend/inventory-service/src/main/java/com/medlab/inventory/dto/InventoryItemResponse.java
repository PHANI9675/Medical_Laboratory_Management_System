package com.medlab.inventory.dto;

import lombok.Data;

@Data
public class InventoryItemResponse {
    private Long id;
    private String itemName;
    private Integer quantity;
    private String unit;
    private String description;
    private Integer lowStockThreshold;
    private boolean lowStock;   // true if quantity <= lowStockThreshold
}

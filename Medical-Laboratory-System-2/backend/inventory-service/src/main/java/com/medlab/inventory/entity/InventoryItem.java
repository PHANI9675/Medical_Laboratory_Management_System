package com.medlab.inventory.entity;

import jakarta.persistence.*;
import jakarta.validation.constraints.*;
import lombok.Data;

@Data
@Entity
@Table(name = "inventory_items")
public class InventoryItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @NotBlank(message = "Item name is required")
    @Column(name = "item_name", nullable = false)
    private String itemName;

    @NotNull(message = "Quantity is required")
    @Min(value = 0, message = "Quantity cannot be negative")
    @Column(nullable = false)
    private Integer quantity;

    @NotBlank(message = "Unit is required")
    @Column(nullable = false, length = 30)
    private String unit;

    private String description;

    @Column(name = "low_stock_threshold")
    private Integer lowStockThreshold = 10;
}
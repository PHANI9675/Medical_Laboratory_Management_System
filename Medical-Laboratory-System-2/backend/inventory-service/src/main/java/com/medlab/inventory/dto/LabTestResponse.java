package com.medlab.inventory.dto;

import lombok.Data;
import java.math.BigDecimal;

@Data
public class LabTestResponse {
    private Long id;
    private String code;
    private String name;
    private BigDecimal price;
    private Integer turnaroundHours;
    private String description;
}
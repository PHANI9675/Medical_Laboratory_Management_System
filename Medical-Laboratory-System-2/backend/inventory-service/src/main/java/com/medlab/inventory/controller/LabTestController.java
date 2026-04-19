package com.medlab.inventory.controller;

import com.medlab.inventory.dto.LabTestRequest;
import com.medlab.inventory.dto.LabTestResponse;
import com.medlab.inventory.LabTestService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/tests")
@RequiredArgsConstructor
@Tag(name = "Lab Test Catalog", description = "Manage the lab test catalog")
public class LabTestController {

    private final LabTestService service;



    @GetMapping
    @Operation(summary = "Get all available tests")
    public ResponseEntity<List<LabTestResponse>> getAllTests() {
        return ResponseEntity.ok(service.getAllTests());
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a test by ID")
    public ResponseEntity<LabTestResponse> getTestById(@PathVariable Long id) {
        return ResponseEntity.ok(service.getTestById(id));
    }

    @PostMapping
    @Operation(summary = "Add a new test to the catalog")
    public ResponseEntity<LabTestResponse> createTest(@Valid @RequestBody LabTestRequest req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.createTest(req));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Update an existing test")
    public ResponseEntity<LabTestResponse> updateTest(
            @PathVariable Long id,
            @Valid @RequestBody LabTestRequest req) {
        return ResponseEntity.ok(service.updateTest(id, req));
    }
}
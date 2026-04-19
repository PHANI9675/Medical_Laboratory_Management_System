package com.lab_processing.lps.controller;

import com.lab_processing.lps.dto.request.CreateJobRequest;
import com.lab_processing.lps.dto.request.EnterResultRequest;
import com.lab_processing.lps.dto.response.ProcessingJobResponse;
import com.lab_processing.lps.dto.response.ResultResponse;
import com.lab_processing.lps.entity.ProcessingJob;
import com.lab_processing.lps.service.ProcessingJobService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/jobs")
public class ProcessingJobController {

    private final ProcessingJobService processingJobService;

    public ProcessingJobController(ProcessingJobService processingJobService) {
        this.processingJobService = processingJobService;
    }

    // FIX throughout: hasRole('X') → hasAuthority('X')
    // hasRole() prepends ROLE_ automatically — JwtFilter now stores plain strings
    // e.g. "LAB_TECH" not "ROLE_LAB_TECH", so hasRole() always returned 403.

    @PreAuthorize("hasAnyAuthority('LAB_TECH','ADMIN')")
    @GetMapping
    public ResponseEntity<List<ProcessingJobResponse>> getAllJobs() {
        List<ProcessingJobResponse> jobs = processingJobService.getAllJobs()
                .stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
        return ResponseEntity.ok(jobs);
    }

    // ADMIN allowed: Order Service (ADMIN JWT) calls this endpoint to auto-create jobs on sample collection
    @PreAuthorize("hasAnyAuthority('LAB_TECH','ADMIN')")
    @PostMapping
    public ProcessingJobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        ProcessingJob job = processingJobService.createJob(
                request.getSampleId(),
                request.getTestId()
        );
        return mapToResponse(job);
    }

    @PreAuthorize("hasAuthority('LAB_TECH')")
    @PostMapping("/{id}/start")
    public ProcessingJobResponse startProcessing(@PathVariable Long id) {
        return mapToResponse(processingJobService.startProcessing(id));
    }

    @PreAuthorize("hasAuthority('LAB_TECH')")
    @PostMapping("/{id}/qc")
    public ProcessingJobResponse markQCPending(@PathVariable Long id) {
        return mapToResponse(processingJobService.markQCPending(id));
    }

    @PreAuthorize("hasAuthority('LAB_TECH')")
    @PostMapping("/{id}/complete")
    public ProcessingJobResponse completeJob(@PathVariable Long id) {
        return mapToResponse(processingJobService.completeJob(id));
    }

    // FIX: was hasRole('LAB_ADMIN') — LAB_ADMIN is not a role issued by auth-service.
    // Changed to hasAuthority('ADMIN') to match actual roles in the system.
    @PreAuthorize("hasAuthority('ADMIN')")
    @PostMapping("/{id}/cancel")
    public ProcessingJobResponse cancelJob(@PathVariable Long id) {
        return mapToResponse(processingJobService.cancelJob(id));
    }

    @PreAuthorize("hasAuthority('LAB_TECH')")
    @PostMapping("/processing/{sampleId}/result")
    public ResponseEntity<String> enterResult(
            @PathVariable Long sampleId,
            @Valid @RequestBody EnterResultRequest request) {
        processingJobService.enterResult(sampleId, request);
        return ResponseEntity.ok("Result entered successfully");
    }

    // FIX: was hasRole('LAB_ADMIN') → hasAuthority('ADMIN')
    @PreAuthorize("hasAuthority('ADMIN')")
    @PutMapping("/processing/{sampleId}/approve")
    public ResponseEntity<String> approveResult(@PathVariable Long sampleId) {
        processingJobService.approveResult(sampleId);
        return ResponseEntity.ok("Result approved successfully");
    }

    /**
     * Returns the lab result for a sample.
     *
     * Called by Billing Service (via Feign) after payment succeeds so the
     * result can be sent to the patient as a LAB_RESULT notification.
     * Also accessible to LAB_TECH (manual lookup) and PATIENT (own results).
     *
     * The JWT forwarded by Billing is the patient's PAYMENT token,
     * so PATIENT authority must be included here.
     */
    @PreAuthorize("hasAnyAuthority('PATIENT','LAB_TECH','ADMIN')")
    @GetMapping("/results/by-sample/{sampleId}")
    public ResponseEntity<ResultResponse> getResultBySampleId(@PathVariable Long sampleId) {
        return ResponseEntity.ok(processingJobService.getResultBySampleId(sampleId));
    }

    private ProcessingJobResponse mapToResponse(ProcessingJob job) {
        ProcessingJobResponse response = new ProcessingJobResponse();
        response.setId(job.getId());
        response.setSampleId(job.getSampleId());
        response.setTestId(job.getTestId());
        response.setStatus(job.getStatus().name());
        response.setCreatedAt(job.getCreatedAt());
        response.setStartedAt(job.getStartedAt());
        response.setCompletedAt(job.getCompletedAt());
        response.setUpdatedAt(job.getUpdatedAt());
        return response;
    }
}
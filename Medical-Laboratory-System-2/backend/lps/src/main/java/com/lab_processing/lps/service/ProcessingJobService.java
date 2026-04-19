package com.lab_processing.lps.service;

import com.lab_processing.lps.dto.request.EnterResultRequest;
import com.lab_processing.lps.dto.response.ResultResponse;
import com.lab_processing.lps.entity.ProcessingJob;

import java.util.List;

public interface ProcessingJobService {

    List<ProcessingJob> getAllJobs();

    //create a new processing job
    ProcessingJob createJob(Long sampleId, Long testId);

    // start lab processing
    ProcessingJob startProcessing(Long jobId);

    // mark job ready for QC
    ProcessingJob markQCPending(Long jobId);

    // Complete the job after QC
    ProcessingJob completeJob(Long jobId);

    // cancel the job
    ProcessingJob cancelJob(Long jobId);

    void enterResult(Long sampleId, EnterResultRequest request);

    void approveResult(Long sampleId);

    /**
     * Returns the approved lab result for a sample.
     * Called by Billing Service (via Feign) after a patient pays their invoice
     * so the result can be delivered to the patient as a LAB_RESULT notification.
     */
    ResultResponse getResultBySampleId(Long sampleId);
}
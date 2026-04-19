package com.lab_processing.lps.service.impl;

import com.lab_processing.lps.client.BillingClient;
import com.lab_processing.lps.client.OrderClient;
import com.lab_processing.lps.client.OrderDetailResponse;
import com.lab_processing.lps.dto.request.EnterResultRequest;
import com.lab_processing.lps.dto.response.ResultResponse;
import com.lab_processing.lps.entity.ProcessingJob;
import com.lab_processing.lps.entity.QCRecord;
import com.lab_processing.lps.entity.Result;
import com.lab_processing.lps.entity.enums.ProcessingJobStatus;
import com.lab_processing.lps.entity.enums.QCStatus;
import com.lab_processing.lps.entity.enums.ResultStatus;
import com.lab_processing.lps.exception.InvalidJobStateException;
import com.lab_processing.lps.repository.ProcessingJobRepository;
import com.lab_processing.lps.repository.QCRecordRepository;
import com.lab_processing.lps.repository.ResultRepository;
import com.lab_processing.lps.service.ProcessingJobService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@Slf4j
public class ProcessingJobServiceImpl implements ProcessingJobService {

    private final ProcessingJobRepository repository;
    private final ResultRepository resultRepository;
    private final QCRecordRepository qcRecordRepository;
    private final BillingClient billingClient;
    private final OrderClient orderClient;

    public ProcessingJobServiceImpl(
            ProcessingJobRepository repository,
            ResultRepository resultRepository,
            QCRecordRepository qcRecordRepository,
            BillingClient billingClient,
            OrderClient orderClient
    ) {
        this.repository = repository;
        this.resultRepository = resultRepository;
        this.qcRecordRepository = qcRecordRepository;
        this.billingClient = billingClient;
        this.orderClient = orderClient;
    }

    @Override
    public List<ProcessingJob> getAllJobs() {
        return repository.findAll();
    }

    @Override
    public ProcessingJob createJob(Long sampleId, Long testId) {
        ProcessingJob job = new ProcessingJob();
        job.setSampleId(sampleId);
        job.setTestId(testId);
        job.setStatus(ProcessingJobStatus.CREATED);
        job.setCreatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Override
    public ProcessingJob startProcessing(Long jobId) {
        ProcessingJob job = getJob(jobId);
        if (job.getStatus() != ProcessingJobStatus.CREATED &&
                job.getStatus() != ProcessingJobStatus.SAMPLE_RECEIVED) {
            throw new InvalidJobStateException(
                    "Job cannot be started in status: " + job.getStatus());
        }
        job.setStatus(ProcessingJobStatus.IN_PROCESS);
        job.setStartedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Override
    public ProcessingJob markQCPending(Long jobId) {
        ProcessingJob job = getJob(jobId);
        if (job.getStatus() != ProcessingJobStatus.IN_PROCESS) {
            throw new InvalidJobStateException("QC can be marked only after processing");
        }
        job.setStatus(ProcessingJobStatus.QC_PENDING);
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Override
    public ProcessingJob completeJob(Long jobId) {
        ProcessingJob job = getJob(jobId);
        if (job.getStatus() != ProcessingJobStatus.QC_PENDING) {
            throw new InvalidJobStateException("Job cannot be completed before QC");
        }
        job.setStatus(ProcessingJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Override
    public ProcessingJob cancelJob(Long jobId) {
        ProcessingJob job = getJob(jobId);
        if (job.getStatus() == ProcessingJobStatus.COMPLETED) {
            throw new InvalidJobStateException("Completed job cannot be cancelled");
        }
        job.setStatus(ProcessingJobStatus.CANCELLED);
        job.setUpdatedAt(LocalDateTime.now());
        return repository.save(job);
    }

    @Override
    public void enterResult(Long sampleId, EnterResultRequest request) {
        ProcessingJob job = repository.findBySampleId(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("Processing job not found"));

        Result result = new Result();
        result.setProcessingJob(job);
        result.setResult(request.getResult());
        result.setEnteredBy(request.getEnteredBy());
        result.setEnteredAt(LocalDateTime.now());
        result.setStatus(ResultStatus.ENTERED);
        result.setCreatedAt(LocalDateTime.now());
        resultRepository.save(result);

        job.setStatus(ProcessingJobStatus.ENTERED);
        job.setUpdatedAt(LocalDateTime.now());
        repository.save(job);

        // QC CHECK — flag abnormal results automatically
        double value = extractValueFromResultJson(request.getResult());
        if (value > 10.0) {
            QCRecord qcRecord = new QCRecord();
            qcRecord.setProcessingJob(job);
            qcRecord.setRemarks("Abnormal result value detected: " + value);
            qcRecord.setQcStatus("FAILED");
            qcRecord.setStatus(QCStatus.FAILED);
            qcRecord.setCreatedAt(LocalDateTime.now());
            qcRecordRepository.save(qcRecord);
            log.warn("QC ALERT: Job {} flagged — abnormal value={}", job.getId(), value);
        }
    }

    @Override
    public void approveResult(Long sampleId) {
        ProcessingJob job = repository.findBySampleId(sampleId)
                .orElseThrow(() -> new IllegalArgumentException("Processing job not found"));

        Result result = resultRepository.findByProcessingJob(job)
                .orElseThrow(() -> new IllegalArgumentException("Result not found for job"));

        if (result.getStatus() != ResultStatus.ENTERED) {
            throw new InvalidJobStateException("Only ENTERED results can be approved");
        }

        // Approve result
        result.setStatus(ResultStatus.APPROVED);
        result.setUpdatedAt(LocalDateTime.now());
        resultRepository.save(result);

        // Complete job
        job.setStatus(ProcessingJobStatus.COMPLETED);
        job.setCompletedAt(LocalDateTime.now());
        job.setUpdatedAt(LocalDateTime.now());
        repository.save(job);

        // INTEGRATION: resolve orderId from Order Service, then trigger Billing.
        //
        // Pull model:
        //   - LPS asks Order Service: "given sampleId, what is the orderId?"
        //   - LPS passes only orderId to Billing (no patientId or testIds in body)
        //   - Billing fetches patientId + testIds from Order Service itself
        //   - Billing fetches test prices from Inventory Service itself
        //
        // If Order Service is unavailable, billing is NOT triggered — we do NOT use
        // sampleId as a fake orderId (that would corrupt invoice data).
        try {
            OrderDetailResponse orderDetail = orderClient.getOrderBySampleId(job.getSampleId());
            if (orderDetail == null) {
                log.error("OrderService unavailable — billing NOT triggered for sampleId={}. " +
                        "Invoice must be generated manually.", job.getSampleId());
                return;
            }
            Long orderId = orderDetail.getOrderId();
            billingClient.generateInvoice(orderId);
            log.info("Billing triggered for orderId={} (sampleId={})", orderId, sampleId);
        } catch (Exception e) {
            log.error("Failed to trigger billing for sampleId={}: {}", sampleId, e.getMessage());
        }
    }

    @Override
    public ResultResponse getResultBySampleId(Long sampleId) {
        ProcessingJob job = repository.findBySampleId(sampleId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Processing job not found for sampleId=" + sampleId));

        Result result = resultRepository.findByProcessingJob(job)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Result not found for sampleId=" + sampleId));

        return new ResultResponse(
                sampleId,
                job.getTestId(),
                result.getResult(),
                result.getStatus().name(),
                result.getEnteredAt()
        );
    }

    private ProcessingJob getJob(Long jobId) {
        return repository.findById(jobId)
                .orElseThrow(() -> new IllegalArgumentException("ProcessingJob not found: " + jobId));
    }

    private double extractValueFromResultJson(String resultJson) {
        try {
            String valuePart = resultJson.split("value\":")[1];
            String number = valuePart.split(",")[0];
            return Double.parseDouble(number.trim());
        } catch (Exception e) {
            return 0.0;
        }
    }
}

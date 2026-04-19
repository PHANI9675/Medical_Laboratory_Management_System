package com.lab_processing.lps.service;

import com.lab_processing.lps.dto.request.EnterResultRequest;
import com.lab_processing.lps.entity.ProcessingJob;
import com.lab_processing.lps.entity.enums.ProcessingJobStatus;
import com.lab_processing.lps.repository.ProcessingJobRepository;
import com.lab_processing.lps.repository.ResultRepository;
import com.lab_processing.lps.repository.QCRecordRepository;
import com.lab_processing.lps.service.impl.ProcessingJobServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProcessingJobServiceTest {

    @Mock
    private ProcessingJobRepository jobRepository;

    @Mock
    private ResultRepository resultRepository;

    @Mock
    private QCRecordRepository qcRecordRepository;

    @InjectMocks
    private ProcessingJobServiceImpl processingJobService;

    @Test
    void createJob_shouldCreateJobWithCreatedStatus() {
        // given
        ProcessingJob savedJob = new ProcessingJob();
        savedJob.setStatus(ProcessingJobStatus.CREATED);

        when(jobRepository.save(any())).thenReturn(savedJob);

        // when
        ProcessingJob job = processingJobService.createJob(1L, 101L);

        // then
        assertEquals(ProcessingJobStatus.CREATED, job.getStatus());
    }


    @Test
    void enterResult_shouldUpdateJobStatusToEntered() {
        // given
        ProcessingJob job = new ProcessingJob();
        job.setStatus(ProcessingJobStatus.CREATED);

        when(jobRepository.findBySampleId(1L)).thenReturn(Optional.of(job));
        when(jobRepository.save(any())).thenReturn(job);

        EnterResultRequest request = new EnterResultRequest();
        request.setTestId(101L);
        request.setResult("{\"value\":12.5}");
        request.setEnteredBy(301L);

        // when
        processingJobService.enterResult(1L, request);

        // then
        assertEquals(ProcessingJobStatus.ENTERED, job.getStatus());
    }
}

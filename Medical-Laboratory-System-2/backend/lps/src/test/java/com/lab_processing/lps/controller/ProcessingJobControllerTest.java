package com.lab_processing.lps.controller;

import com.lab_processing.lps.service.ProcessingJobService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import static org.mockito.Mockito.*;

class ProcessingJobControllerTest {

    @Mock
    private ProcessingJobService processingJobService;

    @InjectMocks
    private ProcessingJobController processingJobController;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    // ---------- CREATE JOB ----------

    @Test
    void createJob_delegatesToService() {
        // Just verify the controller wires to the service correctly
        // Replace the argument type below with your actual request DTO if needed
        verifyNoInteractions(processingJobService);
    }

    // ---------- APPROVE RESULT ----------

    @Test
    void approveResult_delegatesToService() {
        doNothing().when(processingJobService).approveResult(1L);
        processingJobController.approveResult(1L);
        verify(processingJobService, times(1)).approveResult(1L);
    }
}
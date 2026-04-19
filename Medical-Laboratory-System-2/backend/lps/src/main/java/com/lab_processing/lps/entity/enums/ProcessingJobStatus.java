package com.lab_processing.lps.entity.enums;

public enum ProcessingJobStatus {
    CREATED,          // Job created, no sample yet
    SAMPLE_RECEIVED,  // Sample collected & received
    IN_PROCESS,       // Lab processing ongoing
    QC_PENDING,       // Waiting for QC
    COMPLETED,        // Processing finished successfully
    CANCELLED,
    ENTERED// Job cancelled
}
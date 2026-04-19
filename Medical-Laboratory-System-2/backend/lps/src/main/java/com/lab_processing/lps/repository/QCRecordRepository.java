package com.lab_processing.lps.repository;

import com.lab_processing.lps.entity.QCRecord;
import com.lab_processing.lps.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

// this interface is for the QC Recird entity
// used for storing the retreiving the Quality cotrol evaluation for the lab processings.
public interface QCRecordRepository extends JpaRepository<QCRecord, Long> {

    //it fetches the QC Record related to the specific processing job.
    //RETURNING OPTIONAL QCRecord.
    Optional<QCRecord> findByProcessingJob(ProcessingJob processingJob);
}
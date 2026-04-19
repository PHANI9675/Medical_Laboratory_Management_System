package com.lab_processing.lps.repository;

import com.lab_processing.lps.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;


// this interface is for the ProcessingJob in entity package.
// it basically provides the database access methods
// for managing the lab processing job records

public interface ProcessingJobRepository extends JpaRepository<ProcessingJob, Long> {

    // fetch the processing job using the sample ID.
    //This is used when sample is received fromt he order service.
    Optional<ProcessingJob> findBySampleId(Long sampleId);
}
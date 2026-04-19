package com.lab_processing.lps.repository;

import com.lab_processing.lps.entity.Result;
import com.lab_processing.lps.entity.ProcessingJob;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

//this interface is for the Result entity.
//it handles the database operations related to the lab test results.

public interface ResultRepository extends JpaRepository<Result, Long> {


    //this is fetching result associated to the specific processing jobs.
    //return the optional result.
    Optional<Result> findByProcessingJob(ProcessingJob processingJob);
}
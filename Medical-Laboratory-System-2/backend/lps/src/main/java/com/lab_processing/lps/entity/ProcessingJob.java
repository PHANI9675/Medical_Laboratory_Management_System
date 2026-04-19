package com.lab_processing.lps.entity;
import com.lab_processing.lps.entity.enums.ProcessingJobStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDateTime;



@Entity
@Table(name = "processing_jobs")
@Getter
@Setter
public class ProcessingJob { //represents a single lab processing task for the specific test performed on tje sample colllected from order service

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "sample_id", nullable = false)
    private Long sampleId; //  identifier of the sample being processed , comes from order serviice

    @Column(name = "test_id", nullable = false)
    private Long testId;  //identifier of the test being performed , receives from the order service

    @Column(name = "started_at")
    private LocalDateTime startedAt; // timestamp when lab processing started

    @Column(name = "completed_at")
    private LocalDateTime completedAt; // timestamp when lab processing completed

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProcessingJobStatus status = ProcessingJobStatus.CREATED;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;



}
package com.lab_processing.lps.entity;

import com.lab_processing.lps.entity.enums.ResultStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "results")
@Getter
@Setter
public class Result {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "processing_job_id", nullable = false)
    private ProcessingJob processingJob;

    @Column(columnDefinition = "json")
    private String result;   // this is report result

    @Column(name = "entered_by")
    private Long enteredBy;

    @Column(name = "entered_at")
    private LocalDateTime enteredAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ResultStatus status = ResultStatus.DRAFT;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
}
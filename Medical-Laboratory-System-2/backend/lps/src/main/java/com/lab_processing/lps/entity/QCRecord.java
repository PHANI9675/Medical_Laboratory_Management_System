package com.lab_processing.lps.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.lab_processing.lps.entity.enums.QCStatus;
import java.time.LocalDateTime;


@Entity
@Table(name = "qc_records")
@Getter
@Setter
public class QCRecord {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @OneToOne
    @JoinColumn(name = "processing_job_id", nullable = false)
    private ProcessingJob processingJob;

    @Column(name = "qc_status")
    private String qcStatus;   // status of the qc, if it passes ot not

    @Column(name = "remarks")
    private String remarks;


    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QCStatus status = QCStatus.PENDING;


    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

}
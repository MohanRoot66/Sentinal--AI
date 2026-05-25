package com.example.sentinalAI.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "anomalies")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Anomaly extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "service_id")
    private Service service;

    @Column(name = "anomaly_type")
    private String anomalyType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "anomaly_score")
    private Double anomalyScore;

    @Column(name = "severity")
    private String severity;

    @Column(name = "affected_metric")
    private String affectedMetric;

    @Column(name = "metric_baseline_value")
    private Double metricBaselineValue;

    @Column(name = "metric_actual_value")
    private Double metricActualValue;

    @Column(name = "root_cause_explanation", columnDefinition = "TEXT")
    private String rootCauseExplanation;

    @Column(name = "related_service_id")
    private String relatedServiceId;

    @Column(name = "status")
    private String status;
}


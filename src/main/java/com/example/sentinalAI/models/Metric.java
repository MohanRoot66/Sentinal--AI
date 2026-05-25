package com.example.sentinalAI.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "metrics")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Metric extends BaseEntity {
    @ManyToOne
    @JoinColumn(name = "service_id", nullable = false)
    private Service service;

    @Column(name = "metric_name", nullable = false)
    private String metricName; // latency, throughput, error_rate, cpu_usage, memory_usage

    @Column(name = "metric_value", nullable = false)
    private Double metricValue;

    @Column(name = "unit")
    private String unit; // ms, %, requests/sec, etc.

    @Column(name = "timestamp", nullable = false)
    private LocalDateTime timestamp;

    @Column(name = "is_anomaly")
    private Boolean isAnomaly = false;

    @Column(name = "anomaly_score")
    private Double anomalyScore; // 0-1 confidence level
}


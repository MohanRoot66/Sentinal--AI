package com.example.sentinalAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MetricDTO {
    private String serviceId;
    private String metricName;
    private Double metricValue;
    private String unit;
    private LocalDateTime timestamp;
    private Boolean isAnomaly;
    private Double anomalyScore;
}


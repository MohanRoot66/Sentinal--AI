package com.example.sentinalAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AnomalyDTO {
    private String serviceId;
    private String anomalyType;
    private String description;
    private Double anomalyScore;
    private String severity;
    private String affectedMetric;
    private Double metricBaselineValue;
    private Double metricActualValue;
    private String rootCauseExplanation;
    private String relatedServiceId;
    private String status;
}


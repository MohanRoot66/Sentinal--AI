package com.example.sentinalAI.services;

import com.example.sentinalAI.dto.AnomalyDTO;
import com.example.sentinalAI.models.Anomaly;
import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.repositories.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class AnomalyDetectionService {
    private final ServiceRepository serviceRepository;

    public Anomaly detectAnomaly(AnomalyDTO anomalyDTO) {
        Optional<Service> service = serviceRepository.findById(anomalyDTO.getServiceId());
        if (service.isEmpty()) {
            throw new RuntimeException("Service not found: " + anomalyDTO.getServiceId());
        }

        Anomaly anomaly = Anomaly.builder()
                .service(service.get())
                .anomalyType(anomalyDTO.getAnomalyType())
                .description(anomalyDTO.getDescription())
                .detectedAt(LocalDateTime.now())
                .anomalyScore(anomalyDTO.getAnomalyScore())
                .severity(anomalyDTO.getSeverity())
                .affectedMetric(anomalyDTO.getAffectedMetric())
                .metricBaselineValue(anomalyDTO.getMetricBaselineValue())
                .metricActualValue(anomalyDTO.getMetricActualValue())
                .rootCauseExplanation(anomalyDTO.getRootCauseExplanation())
                .relatedServiceId(anomalyDTO.getRelatedServiceId())
                .status("DETECTED")
                .build();

        return anomaly;
    }

    /**
     * Simulates anomaly detection based on metric values
     */
    public boolean isAnomalous(Double currentValue, Double baselineValue, Double threshold) {
        if (baselineValue == null || baselineValue == 0) {
            return false;
        }
        double percentageChange = Math.abs((currentValue - baselineValue) / baselineValue) * 100;
        return percentageChange > threshold;
    }

    /**
     * Calculate severity based on anomaly score
     */
    public String calculateSeverity(Double anomalyScore) {
        if (anomalyScore >= 0.9) {
            return "CRITICAL";
        } else if (anomalyScore >= 0.7) {
            return "HIGH";
        } else if (anomalyScore >= 0.5) {
            return "MEDIUM";
        } else {
            return "LOW";
        }
    }
}


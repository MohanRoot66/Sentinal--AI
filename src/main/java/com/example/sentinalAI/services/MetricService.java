package com.example.sentinalAI.services;

import com.example.sentinalAI.dto.MetricDTO;
import com.example.sentinalAI.models.Metric;
import com.example.sentinalAI.repositories.MetricRepository;
import com.example.sentinalAI.repositories.ServiceRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class MetricService {
    private final MetricRepository metricRepository;
    private final ServiceRepository serviceRepository;

    public Metric ingestMetric(MetricDTO metricDTO) {
        Optional<com.example.sentinalAI.models.Service> service = serviceRepository.findById(metricDTO.getServiceId());
        if (service.isEmpty()) {
            throw new RuntimeException("Service not found: " + metricDTO.getServiceId());
        }

        Metric metric = Metric.builder()
                .service(service.get())
                .metricName(metricDTO.getMetricName())
                .metricValue(metricDTO.getMetricValue())
                .unit(metricDTO.getUnit())
                .timestamp(metricDTO.getTimestamp() != null ? metricDTO.getTimestamp() : LocalDateTime.now())
                .isAnomaly(false)
                .anomalyScore(0.0)
                .build();

        return metricRepository.save(metric);
    }

    public List<Metric> getMetricsByService(String serviceId, String metricName,
                                           LocalDateTime startTime, LocalDateTime endTime) {
        return metricRepository.findByServiceIdAndMetricNameAndTimestampBetween(
                serviceId, metricName, startTime, endTime);
    }

    public List<Metric> getAnomalousMetrics() {
        return metricRepository.findByIsAnomalyTrue();
    }

    public void markAsAnomaly(String metricId, Double anomalyScore, String explanation) {
        Optional<Metric> metric = metricRepository.findById(metricId);
        if (metric.isPresent()) {
            Metric m = metric.get();
            m.setIsAnomaly(true);
            m.setAnomalyScore(anomalyScore);
            metricRepository.save(m);
        }
    }
}

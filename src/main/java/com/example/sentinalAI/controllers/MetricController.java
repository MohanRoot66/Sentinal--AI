package com.example.sentinalAI.controllers;

import com.example.sentinalAI.dto.MetricDTO;
import com.example.sentinalAI.models.Metric;
import com.example.sentinalAI.services.MetricService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/metrics")
@RequiredArgsConstructor
@Tag(name = "Metrics", description = "Metrics collection and retrieval endpoints")
public class MetricController {
    private final MetricService metricService;

    @PostMapping("/ingest")
    @Operation(summary = "Ingest a new metric", description = "Receive and store a metric from monitoring systems")
    public ResponseEntity<Metric> ingestMetric(@RequestBody MetricDTO metricDTO) {
        Metric metric = metricService.ingestMetric(metricDTO);
        return ResponseEntity.ok(metric);
    }

    @GetMapping("/service/{serviceId}")
    @Operation(summary = "Get metrics by service", description = "Retrieve metrics for a specific service")
    public ResponseEntity<List<Metric>> getMetricsByService(
            @PathVariable String serviceId,
            @RequestParam String metricName,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusHours(1);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        List<Metric> metrics = metricService.getMetricsByService(serviceId, metricName, start, end);
        return ResponseEntity.ok(metrics);
    }

    @GetMapping("/anomalies")
    @Operation(summary = "Get anomalous metrics", description = "Retrieve all metrics marked as anomalies")
    public ResponseEntity<List<Metric>> getAnomalousMetrics() {
        List<Metric> metrics = metricService.getAnomalousMetrics();
        return ResponseEntity.ok(metrics);
    }
}


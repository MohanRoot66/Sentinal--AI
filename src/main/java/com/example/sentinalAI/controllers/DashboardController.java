package com.example.sentinalAI.controllers;

import com.example.sentinalAI.models.Metric;
import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.services.MetricService;
import com.example.sentinalAI.services.ServiceManagementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;
import java.util.LinkedHashMap;

@RestController
@RequestMapping("/api/v1/dashboard")
@RequiredArgsConstructor
@Tag(name = "Dashboard", description = "Dashboard summary data")
public class DashboardController {

    private final ServiceManagementService serviceManagementService;
    private final MetricService metricService;

    @GetMapping
    @Operation(summary = "Get dashboard summary", description = "Returns all services, anomalous metrics in one call")
    public ResponseEntity<Map<String, Object>> getDashboard() {
        List<Service> services = serviceManagementService.getAllServices();
        List<Metric> anomalies = metricService.getAnomalousMetrics();

        Map<String, Object> dashboard = new LinkedHashMap<>();
        dashboard.put("services", services);
        dashboard.put("anomalousMetrics", anomalies);
        dashboard.put("totalServices", services.size());
        dashboard.put("totalAnomalies", anomalies.size());

        return ResponseEntity.ok(dashboard);
    }
}


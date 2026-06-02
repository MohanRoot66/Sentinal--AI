package com.example.sentinalAI.controllers;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.services.AIOrchestrator;
import com.example.sentinalAI.services.LogAnalysisPipeline;
import com.example.sentinalAI.services.LogGeneratorService;
import com.example.sentinalAI.services.LogGeneratorService.Scenario;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/rca")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "RCA", description = "Log generation and AI-powered Root Cause Analysis")
public class RCAController {

    private final LogGeneratorService logGeneratorService;
    private final AIOrchestrator aiOrchestrator;
    private final LogAnalysisPipeline pipeline;
    private final LogRepository logRepository;

    @PostMapping("/generate-logs")
    @Operation(summary = "Generate logs for a scenario",
               description = "Generates realistic application logs for: NORMAL, LATENCY_SPIKE, ERROR_STORM, SERVICE_DOWN, DB_TIMEOUT, MEMORY_LEAK")
    public ResponseEntity<Map<String, Object>> generateLogs(
            @RequestParam(defaultValue = "LATENCY_SPIKE") String scenario) {
        try {
            Scenario s = Scenario.valueOf(scenario.toUpperCase());
            List<Log> logs = logGeneratorService.generateLogs(s);

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("scenario", scenario);
            resp.put("logsGenerated", logs.size());
            resp.put("message", "Logs generated successfully. Call /api/v1/rca/analyze to get AI RCA.");
            resp.put("previewLogs", logs.stream().limit(5).map(this::toLogMap).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid scenario. Valid values: NORMAL, LATENCY_SPIKE, ERROR_STORM, SERVICE_DOWN, DB_TIMEOUT, MEMORY_LEAK"
            ));
        }
    }

    @GetMapping("/logs")
    @Operation(summary = "Get recent logs", description = "Returns logs filtered by scenario (or most recent 200 if no scenario given)")
    public ResponseEntity<Map<String, Object>> getRecentLogs(
            @RequestParam(required = false) String scenario) {
        List<Log> logs = (scenario != null && !scenario.isBlank())
                ? logRepository.findByScenario(scenario.toUpperCase())
                : logRepository.findRecentLogs();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalLogs", logs.size());
        resp.put("logs", logs.stream().map(this::toLogMap).toList());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze logs with AI",
               description = "Runs rule-based anomaly detection pipeline first, then sends only the anomaly report + key evidence to the configured AI (Ollama/Gemini)")
    public ResponseEntity<Map<String, Object>> analyzeWithAI(
            @RequestParam(defaultValue = "UNKNOWN") String scenario) {
        log.info("Starting AI analysis for scenario: {}", scenario);
        List<Log> logs = logRepository.findLogsSince(LocalDateTime.now().minusHours(2));

        if (logs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No logs found. Please call /api/v1/rca/generate-logs first."
            ));
        }

        LogAnalysisPipeline.DetectionReport report = pipeline.run(logs, scenario);

        long start = System.currentTimeMillis();
        String rca = aiOrchestrator.analyzeLogsForRCA(logs, scenario);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("scenario", scenario);
        resp.put("logsAnalyzed", logs.size());
        resp.put("analysisTimeMs", duration);
        resp.put("aiProvider", aiOrchestrator.getActiveProvider());
        resp.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        resp.put("rca", rca);
        resp.put("pipeline", Map.of(
                "rawLogsIngested", logs.size(),
                "anomaliesDetected", report.detectedAnomalies().size(),
                "evidenceLogsSentToAI", report.keyEvidenceLogs().size(),
                "overallSeverity", report.highestSeverity(),
                "errorRate", String.format("%.1f%%", report.errorRate() * 100),
                "peakLatencyMs", report.peakLatencyMs(),
                "detectedAnomalyTypes", report.detectedAnomalies().stream()
                        .map(a -> a.type() + " (" + a.severity() + ")")
                        .toList()
        ));
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/generate-and-analyze")
    @Operation(summary = "Generate logs and immediately analyze with AI",
               description = "One-shot: generates logs then runs AI analysis via configured provider (Ollama/Gemini)")
    public ResponseEntity<Map<String, Object>> generateAndAnalyze(
            @RequestParam(defaultValue = "LATENCY_SPIKE") String scenario) {
        try {
            Scenario s = Scenario.valueOf(scenario.toUpperCase());
            List<Log> logs = logGeneratorService.generateLogs(s);
            log.info("Generated {} logs for scenario {}. Starting AI analysis...", logs.size(), scenario);

            long start = System.currentTimeMillis();
            String rca = aiOrchestrator.analyzeLogsForRCA(logs, scenario);
            long duration = System.currentTimeMillis() - start;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("scenario", scenario);
            resp.put("logsGenerated", logs.size());
            resp.put("analysisTimeMs", duration);
            resp.put("aiProvider", aiOrchestrator.getActiveProvider());
            resp.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
            resp.put("rca", rca);
            resp.put("logSample", logs.stream().limit(10).map(this::toLogMap).toList());
            return ResponseEntity.ok(resp);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "Invalid scenario. Valid: NORMAL, LATENCY_SPIKE, ERROR_STORM, SERVICE_DOWN, DB_TIMEOUT, MEMORY_LEAK"
            ));
        }
    }

    private Map<String, Object> toLogMap(Log l) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("timestamp", l.getTimestamp() != null ? l.getTimestamp().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")) : null);
        m.put("level", l.getLogLevel());
        m.put("service", l.getService() != null ? l.getService().getServiceName() : null);
        m.put("message", l.getMessage());
        m.put("statusCode", l.getStatusCode());
        m.put("latencyMs", l.getLatencyMs());
        m.put("traceId", l.getTraceId());
        return m;
    }
}

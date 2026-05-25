package com.example.sentinalAI.controllers;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.services.HuggingFaceService;
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
    private final HuggingFaceService huggingFaceService;
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
    @Operation(summary = "Get recent logs", description = "Returns the most recent 200 logs stored in the database")
    public ResponseEntity<Map<String, Object>> getRecentLogs() {
        List<Log> logs = logRepository.findRecentLogs();
        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("totalLogs", logs.size());
        resp.put("logs", logs.stream().map(this::toLogMap).toList());
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/analyze")
    @Operation(summary = "Analyze logs with AI",
               description = "Sends stored logs to HuggingFace model for AI-powered Root Cause Analysis")
    public ResponseEntity<Map<String, Object>> analyzeWithAI(
            @RequestParam(defaultValue = "UNKNOWN") String scenario) {
        log.info("Starting AI analysis for scenario: {}", scenario);
        List<Log> logs = logRepository.findLogsSince(LocalDateTime.now().minusHours(2));

        if (logs.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of(
                    "error", "No logs found. Please call /api/v1/rca/generate-logs first."
            ));
        }

        long start = System.currentTimeMillis();
        String rca = huggingFaceService.analyzeLogsForRCA(logs, scenario);
        long duration = System.currentTimeMillis() - start;

        Map<String, Object> resp = new LinkedHashMap<>();
        resp.put("scenario", scenario);
        resp.put("logsAnalyzed", logs.size());
        resp.put("analysisTimeMs", duration);
        resp.put("generatedAt", LocalDateTime.now().format(DateTimeFormatter.ISO_LOCAL_DATE_TIME));
        resp.put("rca", rca);
        return ResponseEntity.ok(resp);
    }

    @PostMapping("/generate-and-analyze")
    @Operation(summary = "Generate logs and immediately analyze with AI",
               description = "One-shot: generates logs for the scenario then runs AI analysis")
    public ResponseEntity<Map<String, Object>> generateAndAnalyze(
            @RequestParam(defaultValue = "LATENCY_SPIKE") String scenario) {
        try {
            Scenario s = Scenario.valueOf(scenario.toUpperCase());
            List<Log> logs = logGeneratorService.generateLogs(s);
            log.info("Generated {} logs for scenario {}. Starting AI analysis...", logs.size(), scenario);

            long start = System.currentTimeMillis();
            String rca = huggingFaceService.analyzeLogsForRCA(logs, scenario);
            long duration = System.currentTimeMillis() - start;

            Map<String, Object> resp = new LinkedHashMap<>();
            resp.put("scenario", scenario);
            resp.put("logsGenerated", logs.size());
            resp.put("analysisTimeMs", duration);
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


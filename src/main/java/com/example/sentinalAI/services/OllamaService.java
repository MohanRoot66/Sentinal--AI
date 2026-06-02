package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.services.LogAnalysisPipeline.DetectedAnomaly;
import com.example.sentinalAI.services.LogAnalysisPipeline.DetectionReport;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

/**
 * OllamaService — calls a locally running Ollama instance.
 *
 * Ollama exposes an OpenAI-compatible endpoint:
 *   POST http://localhost:11434/v1/chat/completions
 *
 * Recommended models (run once in terminal before starting app):
 *   ollama pull llama3          (8B, good quality, ~5GB)
 *   ollama pull mistral         (7B, fast, ~4GB)
 *   ollama pull gemma3:4b       (4B, lightweight, ~3GB)
 *   ollama pull qwen2.5:3b      (3B, very fast, ~2GB)
 *
 * Pipeline flow (same as Gemini):
 *   Raw logs → Rule-based detection → Evidence extraction → Ollama RCA
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class OllamaService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogAnalysisPipeline pipeline;

    @Value("${ollama.base-url:http://localhost:11434}")
    private String baseUrl;

    @Value("${ollama.model:llama3}")
    private String model;

    @Value("${ollama.max-tokens:2048}")
    private int maxTokens;

    @Value("${ollama.temperature:0.3}")
    private double temperature;

    @Value("${ollama.connect-timeout:5000}")
    private int connectTimeout;

    @Value("${ollama.read-timeout:180000}")
    private int readTimeout;

    // ─── Public API ───────────────────────────────────────────────────────────

    public boolean isAvailable() {
        try {
            RestClient.builder().baseUrl(baseUrl).build()
                    .get().uri("/api/tags").retrieve().toBodilessEntity();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Full pipeline:
     *  Stage 1-3 : Rule-based anomaly detection + evidence extraction (LogAnalysisPipeline)
     *  Stage 4   : Send structured anomaly report (NOT raw logs) to local Ollama model
     */
    public String analyzeLogsForRCA(List<Log> rawLogs, String scenario) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return "No logs available for analysis. Please generate logs first.";
        }

        DetectionReport report = pipeline.run(rawLogs, scenario);

        String prompt = buildPipelinePrompt(report);
        log.info("Calling Ollama | model: {} | url: {} | raw logs: {} → anomalies: {} → evidence: {}",
                model, baseUrl, rawLogs.size(), report.detectedAnomalies().size(), report.keyEvidenceLogs().size());

        try {
            String content = callOllama(prompt);
            return content != null ? content : fallbackRCA(report, "Empty response from Ollama.");
        } catch (RestClientException e) {
            log.error("Ollama API error: {}", e.getMessage());
            return fallbackRCA(report, "Cannot reach Ollama at " + baseUrl + " — is Ollama running? Error: " + e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected Ollama error: {}", e.getMessage());
            return fallbackRCA(report, e.getMessage());
        }
    }

    public String answerQuestion(String question, String logContext) {
        // Keep prompt short for speed — only the most relevant context
        String prompt = "You are an SRE assistant. Answer briefly and technically.\n\n"
                + "CONTEXT:\n" + logContext.substring(0, Math.min(logContext.length(), 1500)) + "\n\n"
                + "QUESTION: " + question + "\n\n"
                + "Answer in 3-5 sentences max. Be direct.";

        try {
            String answer = callOllama(prompt);
            return answer != null ? answer : "I couldn't generate a response. Is Ollama running?";
        } catch (RestClientException e) {
            log.error("Ollama chat error: {}", e.getMessage());
            return "⚠️ Cannot reach Ollama at " + baseUrl + ". Make sure `ollama serve` is running and model `" + model + "` is pulled. Error: " + e.getMessage();
        } catch (Exception e) {
            return "⚠️ Unexpected error: " + e.getMessage();
        }
    }

    // ─── Ollama HTTP Call (OpenAI-compatible endpoint) ─────────────────────

    @SuppressWarnings("unchecked")
    private String callOllama(String prompt) {
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .build();

        // Ollama uses the OpenAI-compatible /v1/chat/completions format
        Map<String, Object> message = Map.of("role", "user", "content", prompt);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("messages", List.of(message));
        body.put("max_tokens", maxTokens);
        body.put("temperature", temperature);
        body.put("stream", false);

        Map<String, Object> response = client.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) return null;
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) return null;
            Map<String, Object> msg = (Map<String, Object>) choices.get(0).get("message");
            return (String) msg.get("content");
        } catch (Exception e) {
            log.error("Failed to parse Ollama response: {}", e.getMessage());
            return null;
        }
    }

    // ─── Prompt Builder ───────────────────────────────────────────────────────

    private String buildPipelinePrompt(DetectionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("You are a senior Site Reliability Engineer AI.\n");
        sb.append("Below is a structured anomaly detection report from Sentinel AI's rule-based pipeline.\n");
        sb.append("Perform a Root Cause Analysis based ONLY on this report.\n\n");

        sb.append(String.format(
                "SCENARIO: %s | SEVERITY: %s | TOTAL LOGS: %d | ERRORS: %d (%.1f%%) | WARNINGS: %d\n",
                report.scenario(), report.highestSeverity(), report.totalLogs(),
                report.errorCount(), report.errorRate() * 100, report.warnCount()));
        sb.append(String.format("PEAK LATENCY: %dms | p95 LATENCY: %dms\n\n",
                report.peakLatencyMs(), report.p95LatencyMs()));

        sb.append("=== RULE-BASED ANOMALIES DETECTED ===\n");
        if (report.detectedAnomalies().isEmpty()) {
            sb.append("None — system appears healthy.\n");
        } else {
            for (int i = 0; i < report.detectedAnomalies().size(); i++) {
                DetectedAnomaly a = report.detectedAnomalies().get(i);
                sb.append(String.format("[%d] %s | %s | %.0f%% confidence\n    %s\n",
                        i + 1, a.type(), a.severity(), a.confidenceScore() * 100, a.description()));
            }
        }

        if (!report.errorsByService().isEmpty()) {
            sb.append("\n=== ERRORS BY SERVICE ===\n");
            report.errorsByService().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  %s → %d errors\n", e.getKey(), e.getValue())));
        }

        sb.append(String.format("\n=== KEY EVIDENCE LOGS (%d of %d total selected by rules) ===\n",
                report.keyEvidenceLogs().size(), report.totalLogs()));
        report.keyEvidenceLogs().forEach(l -> sb.append(String.format(
                "[%s][%s][%s] status=%s latency=%s %s\n",
                l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "?",
                l.getLogLevel() != null ? l.getLogLevel() : "?",
                l.getService() != null ? l.getService().getServiceName() : "?",
                l.getStatusCode() != null ? l.getStatusCode() : "?",
                l.getLatencyMs() != null ? l.getLatencyMs() + "ms" : "?",
                l.getMessage() != null ? l.getMessage() : "")));

        sb.append("""

                === YOUR TASK ===
                Write a Root Cause Analysis with these sections:

                ## Problem Summary
                (1-2 sentence summary)

                ## Root Cause
                (Technical root cause citing specific anomaly evidence)

                ## Impact Analysis
                (Affected services, error rates, latency, estimated user impact)

                ## Immediate Remediation Steps
                (Numbered action items, most urgent first)

                ## Long-term Recommendations
                (Prevent recurrence)
                """);

        return sb.toString();
    }

    // ─── Fallback ─────────────────────────────────────────────────────────────

    private String fallbackRCA(DetectionReport report, String errorMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append("⚠️ **Ollama Unavailable** — Showing rule-based analysis.\n");
        sb.append("*Error: ").append(errorMsg).append("*\n\n");
        sb.append("**Fix:** Run these commands in a terminal:\n");
        sb.append("```\n");
        sb.append("ollama serve\n");
        sb.append("ollama pull ").append(model).append("\n");
        sb.append("```\n\n---\n\n");

        sb.append("## Rule-Based Detection Results\n");
        if (report.detectedAnomalies().isEmpty()) {
            sb.append("No anomalies detected.\n");
        } else {
            report.detectedAnomalies().forEach(a ->
                    sb.append(String.format("- **[%s]** (%s, %.0f%% confidence): %s\n",
                            a.type(), a.severity(), a.confidenceScore() * 100, a.description())));
        }

        sb.append(String.format("\n## Stats\n- Total logs: %d\n- Errors: %d (%.1f%%)\n- Warnings: %d\n- Peak latency: %dms\n",
                report.totalLogs(), report.errorCount(), report.errorRate() * 100,
                report.warnCount(), report.peakLatencyMs()));

        return sb.toString();
    }
}


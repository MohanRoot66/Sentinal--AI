package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.services.LogAnalysisPipeline.DetectionReport;
import com.example.sentinalAI.services.LogAnalysisPipeline.DetectedAnomaly;
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

@Service
@Slf4j
@RequiredArgsConstructor
public class GeminiService {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final LogAnalysisPipeline pipeline;

    @Value("${google.gemini.api-key:}")
    private String apiKey;

    @Value("${google.gemini.model:gemini-2.0-flash}")
    private String model;

    @Value("${google.gemini.max-tokens:2048}")
    private int maxTokens;

    @Value("${google.gemini.temperature:0.3}")
    private double temperature;

    // ─── Public API ───────────────────────────────────────────────────────────

    /**
     * Full pipeline:
     *   1. Run rule-based anomaly detection on raw logs (LogAnalysisPipeline)
     *   2. Extract only the key evidence logs (≤ 20)
     *   3. Send the structured anomaly report + evidence to Gemini
     *
     * Gemini NEVER sees raw 200-log dumps — only the curated anomaly report.
     */
    public String analyzeLogsForRCA(List<Log> rawLogs, String scenario) {
        if (rawLogs == null || rawLogs.isEmpty()) {
            return "No logs available for analysis. Please generate logs first.";
        }

        // ── Stage 1-3: Run the analysis pipeline ──────────────────────────────
        DetectionReport report = pipeline.run(rawLogs, scenario);

        if (!isConfigured()) {
            return fallbackRCA(report, "Google Gemini API key not configured. Set `google.gemini.api-key` in application.properties.");
        }

        // ── Stage 4: Build a structured prompt from the report (NOT raw logs) ─
        String prompt = buildPipelinePrompt(report);
        log.info("Calling Gemini | model: {} | raw logs: {} → anomalies: {} → evidence logs: {}",
                model, rawLogs.size(), report.detectedAnomalies().size(), report.keyEvidenceLogs().size());

        try {
            String content = callGemini(prompt);
            return content != null ? content : fallbackRCA(report, "Empty response from Gemini.");
        } catch (RestClientException e) {
            log.error("Gemini API error during RCA: {}", e.getMessage());
            return fallbackRCA(report, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected Gemini error: {}", e.getMessage());
            return fallbackRCA(report, e.getMessage());
        }
    }

    public String answerQuestion(String question, String logContext) {
        if (!isConfigured()) {
            return "⚠️ Google Gemini API key not configured. Please set `google.gemini.api-key` in `application.properties` and restart.";
        }

        String prompt = """
                You are a senior SRE AI assistant for Sentinel AI monitoring platform.
                Your job: answer questions about system health, logs, incidents and root causes.

                ## Current System State
                %s

                ## User Question
                %s

                Answer concisely and technically. Cite specific log evidence where possible. If you can suggest a fix, do so.
                """.formatted(logContext, question);

        try {
            String answer = callGemini(prompt);
            return answer != null ? answer : "I couldn't generate a response. Please try again.";
        } catch (RestClientException e) {
            log.error("Gemini chat error: {}", e.getMessage());
            return "⚠️ Error contacting Gemini API: " + e.getMessage();
        } catch (Exception e) {
            log.error("Unexpected Gemini chat error: {}", e.getMessage());
            return "⚠️ Unexpected error: " + e.getMessage();
        }
    }

    // ─── Prompt Builder (uses DetectionReport, NOT raw logs) ─────────────────

    private String buildPipelinePrompt(DetectionReport report) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
                You are a senior Site Reliability Engineer AI. Below is a structured anomaly detection report
                produced by Sentinel AI's rule-based pipeline. Your job is to perform deep Root Cause Analysis.

                ═══════════════════════════════════════════════════════════════
                ANOMALY DETECTION REPORT  (produced by rule-based engine)
                ═══════════════════════════════════════════════════════════════

                """);

        // Summary stats
        sb.append(String.format("""
                Scenario        : %s
                Total Log Events: %d
                Error Count     : %d (%.1f%%)
                Warning Count   : %d
                Peak Latency    : %dms
                p95 Latency     : %dms
                Overall Severity: %s

                """,
                report.scenario(), report.totalLogs(), report.errorCount(),
                report.errorRate() * 100, report.warnCount(),
                report.peakLatencyMs(), report.p95LatencyMs(),
                report.highestSeverity()));

        // Detected anomalies
        sb.append("── RULE-BASED ANOMALIES DETECTED ──────────────────────────────\n");
        if (report.detectedAnomalies().isEmpty()) {
            sb.append("No anomalies detected. System appears healthy.\n");
        } else {
            for (int i = 0; i < report.detectedAnomalies().size(); i++) {
                DetectedAnomaly a = report.detectedAnomalies().get(i);
                sb.append(String.format("[%d] %s | Severity: %s | Confidence: %.0f%%\n    %s\n\n",
                        i + 1, a.type(), a.severity(), a.confidenceScore() * 100, a.description()));
            }
        }

        // Errors by service
        if (!report.errorsByService().isEmpty()) {
            sb.append("── ERROR DISTRIBUTION BY SERVICE ───────────────────────────\n");
            report.errorsByService().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  %-30s → %d errors\n", e.getKey(), e.getValue())));
            sb.append("\n");
        }

        // Key evidence logs (rule-selected, NOT all logs)
        sb.append(String.format(
                "── KEY EVIDENCE LOGS (%d selected from %d total) ─────────────\n",
                report.keyEvidenceLogs().size(), report.totalLogs()));
        sb.append("(These logs were selected by anomaly rules as most diagnostic)\n\n");
        report.keyEvidenceLogs().forEach(l -> sb.append(String.format(
                "[%s] [%-5s] [%-25s] status=%-3s latency=%-6s %s\n",
                l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "?",
                l.getLogLevel() != null ? l.getLogLevel() : "?",
                l.getService() != null ? l.getService().getServiceName() : "?",
                l.getStatusCode() != null ? l.getStatusCode() : "?",
                l.getLatencyMs() != null ? l.getLatencyMs() + "ms" : "?",
                l.getMessage() != null ? l.getMessage() : "")));

        sb.append("""

                ═══════════════════════════════════════════════════════════════
                Based on the anomaly report above, produce a Root Cause Analysis:

                ## 🔍 Problem Summary
                (1-2 sentence executive summary)

                ## 🎯 Root Cause
                (Specific technical root cause with references to the anomaly evidence above)

                ## 📊 Impact Analysis
                (Services affected, error rates, latency impact, estimated user impact)

                ## 🔧 Immediate Remediation Steps
                (Numbered, prioritised action items)

                ## 🛡️ Long-term Recommendations
                (Architectural / process improvements to prevent recurrence)
                """);

        return sb.toString();
    }

    // ─── Fallback (no Gemini) ─────────────────────────────────────────────────

    private String fallbackRCA(DetectionReport report, String errorMsg) {
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(
                "⚠️ **Gemini AI Unavailable** — Showing pipeline rule-based analysis instead.\n*Error: %s*\n\n---\n\n",
                errorMsg));

        sb.append("## 🔍 Problem Summary\n");
        sb.append(String.format("Scenario **%s** — %d anomalies detected across %d log events.\n\n",
                report.scenario(), report.detectedAnomalies().size(), report.totalLogs()));

        sb.append("## 🧠 Rule-Based Anomaly Detection Results\n");
        if (report.detectedAnomalies().isEmpty()) {
            sb.append("No anomalies detected. System appears healthy.\n");
        } else {
            report.detectedAnomalies().forEach(a ->
                    sb.append(String.format("- **[%s]** (%s, %.0f%% confidence): %s\n",
                            a.type(), a.severity(), a.confidenceScore() * 100, a.description())));
        }

        sb.append("\n## 📊 Impact Analysis\n");
        sb.append(String.format("- Total log events: %d\n- Errors: %d (%.1f%%)\n- Warnings: %d\n- Peak latency: %dms\n- p95 latency: %dms\n",
                report.totalLogs(), report.errorCount(), report.errorRate() * 100,
                report.warnCount(), report.peakLatencyMs(), report.p95LatencyMs()));

        if (!report.errorsByService().isEmpty()) {
            sb.append("\n**Errors by Service:**\n");
            report.errorsByService().entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .forEach(e -> sb.append(String.format("  - %s: %d errors\n", e.getKey(), e.getValue())));
        }

        sb.append("\n## 🔒 To Enable AI-Powered RCA\n");
        sb.append("1. Get a free key at https://aistudio.google.com/app/apikey\n");
        sb.append("2. Set `google.gemini.api-key=YOUR_KEY` in `application.properties`\n");
        sb.append("3. Restart the application\n");

        return sb.toString();
    }

    // ─── Gemini HTTP Call ─────────────────────────────────────────────────────

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("YOUR_GOOGLE_API_KEY");
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        RestClient client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();

        Map<String, Object> part    = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> genConfig = Map.of("maxOutputTokens", maxTokens, "temperature", temperature);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("contents", List.of(content));
        body.put("generationConfig", genConfig);

        String url = "/v1beta/models/" + model + ":generateContent?key=" + apiKey;

        Map<String, Object> response = client.post()
                .uri(url)
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(Map.class);

        if (response == null) return null;
        try {
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) return null;
            Map<String, Object> cContent = (Map<String, Object>) candidates.get(0).get("content");
            List<Map<String, Object>> parts = (List<Map<String, Object>>) cContent.get("parts");
            return (String) parts.get(0).get("text");
        } catch (Exception e) {
            log.error("Failed to parse Gemini response: {}", e.getMessage());
            return null;
        }
    }
}

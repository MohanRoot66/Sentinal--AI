package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
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
public class GeminiService {

    private static final String GEMINI_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent?key={key}";

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Value("${google.gemini.api-key:}")
    private String apiKey;

    @Value("${google.gemini.model:gemini-1.5-flash}")
    private String model;

    @Value("${google.gemini.max-tokens:2048}")
    private int maxTokens;

    @Value("${google.gemini.temperature:0.3}")
    private double temperature;

    // ─── Public API ───────────────────────────────────────────────────────────

    public String analyzeLogsForRCA(List<Log> logs, String scenario) {
        if (logs == null || logs.isEmpty()) {
            return "No logs available for analysis. Please generate logs first.";
        }
        if (!isConfigured()) {
            return fallbackRCA(logs, scenario, "Google Gemini API key not configured. Set `google.gemini.api-key` in application.properties.");
        }

        String prompt = buildRCAPrompt(formatLogs(logs), scenario);
        log.info("Calling Gemini API | model: {} | logs: {}", model, logs.size());

        try {
            String content = callGemini(prompt);
            return content != null ? content : fallbackRCA(logs, scenario, "Empty response from Gemini.");
        } catch (RestClientException e) {
            log.error("Gemini API error during RCA: {}", e.getMessage());
            return fallbackRCA(logs, scenario, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected Gemini error: {}", e.getMessage());
            return fallbackRCA(logs, scenario, e.getMessage());
        }
    }

    public String answerQuestion(String question, String logContext) {
        if (!isConfigured()) {
            return "⚠️ Google Gemini API key not configured. Please set `google.gemini.api-key` in `application.properties` and restart.";
        }

        String prompt = """
                You are a senior SRE AI assistant for Sentinel AI monitoring platform.
                Your job: answer questions about system health, logs, incidents and root causes.

                ## Current Log Context
                %s

                ## User Question
                %s

                Answer concisely and technically. If you can suggest a fix, do so.
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

    // ─── Internal ─────────────────────────────────────────────────────────────

    private boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank() && !apiKey.equals("YOUR_GOOGLE_API_KEY");
    }

    @SuppressWarnings("unchecked")
    private String callGemini(String prompt) {
        RestClient client = RestClient.builder()
                .baseUrl("https://generativelanguage.googleapis.com")
                .build();

        Map<String, Object> part = Map.of("text", prompt);
        Map<String, Object> content = Map.of("parts", List.of(part));
        Map<String, Object> genConfig = Map.of(
                "maxOutputTokens", maxTokens,
                "temperature", temperature
        );
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

    private String formatLogs(List<Log> logs) {
        return logs.stream()
                .limit(80)
                .map(l -> String.format("[%s] [%s] [%s] status=%s latency=%sms %s",
                        l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "?",
                        l.getLogLevel() != null ? l.getLogLevel() : "?",
                        l.getService() != null ? l.getService().getServiceName() : "?",
                        l.getStatusCode() != null ? l.getStatusCode() : "?",
                        l.getLatencyMs() != null ? l.getLatencyMs() : "?",
                        l.getMessage() != null ? l.getMessage() : ""))
                .collect(Collectors.joining("\n"));
    }

    private String buildRCAPrompt(String logs, String scenario) {
        return """
                You are a senior Site Reliability Engineer AI. Analyse the following application logs and produce a Root Cause Analysis report.

                ## Scenario
                %s

                ## Application Logs
                ```
                %s
                ```

                ## Required Report Structure
                ## 🔍 Problem Summary
                (1-2 sentence executive summary)

                ## 🎯 Root Cause
                (Specific technical root cause with evidence from logs)

                ## 📊 Impact Analysis
                (Services affected, error rates, latency impact)

                ## 🔧 Immediate Remediation Steps
                (Numbered action items, most urgent first)

                ## 🛡️ Long-term Recommendations
                (Architectural / process improvements)

                Be precise and cite specific log entries as evidence.
                """.formatted(scenario, logs);
    }

    private String fallbackRCA(List<Log> logs, String scenario, String errorMsg) {
        long errors = logs.stream().filter(l -> "ERROR".equals(l.getLogLevel())).count();
        long warns  = logs.stream().filter(l -> "WARN".equals(l.getLogLevel())).count();
        OptionalLong peak = logs.stream().filter(l -> l.getLatencyMs() != null)
                .mapToLong(Log::getLatencyMs).max();

        String ruleBasedCause = switch (scenario) {
            case "LATENCY_SPIKE"  -> "High latency detected in upstream service causing cascading delays. Check connection pool exhaustion and retry configuration.";
            case "ERROR_STORM"    -> "Spike in error rate across multiple services. Likely a bad deployment or downstream dependency failure.";
            case "SERVICE_DOWN"   -> "Service is returning 5xx errors or not responding. Check pod/container health and recent deployments.";
            case "DB_TIMEOUT"     -> "Database queries are timing out. Possible causes: missing indexes, lock contention, or DB overload.";
            case "MEMORY_LEAK"    -> "Memory usage growing unbounded. Look for unclosed resources, large caches, or retained references.";
            default               -> "Review error patterns and service dependencies for root cause.";
        };

        return """
                ⚠️ **Gemini API Unavailable** — Showing rule-based analysis instead.
                *Error: %s*

                ---

                ## 🔍 Problem Summary
                Scenario **%s** detected. Found **%d ERROR** and **%d WARN** events across %d total log entries.

                ## 🎯 Root Cause (Rule-Based)
                %s

                ## 📊 Impact Analysis
                - Total logs analysed: %d
                - Error events: %d (%.1f%%)
                - Warning events: %d
                - Peak latency observed: %s

                ## 🔒 To Enable AI-Powered RCA
                1. Get a free Google Gemini key at https://aistudio.google.com/app/apikey
                2. Update `application.properties`:
                   `google.gemini.api-key=YOUR_KEY_HERE`
                3. Restart the application
                """.formatted(
                errorMsg, scenario, errors, warns, logs.size(),
                ruleBasedCause,
                logs.size(), errors,
                logs.isEmpty() ? 0.0 : errors * 100.0 / logs.size(),
                warns,
                peak.isPresent() ? peak.getAsLong() + "ms" : "N/A"
        );
    }
}


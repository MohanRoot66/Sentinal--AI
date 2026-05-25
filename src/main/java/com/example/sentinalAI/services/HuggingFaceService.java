package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.format.DateTimeFormatter;
import java.util.*;

@Service
@Slf4j
public class HuggingFaceService {

    @Value("${huggingface.api-url}")
    private String apiUrl;

    @Value("${huggingface.api-token:}")
    private String apiToken;

    @Value("${huggingface.model:mistralai/Mistral-7B-Instruct-v0.3}")
    private String model;

    @Value("${huggingface.max-tokens:1500}")
    private int maxTokens;

    @Value("${huggingface.temperature:0.3}")
    private double temperature;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Send logs to HuggingFace model and return AI-generated RCA.
     */
    public String analyzeLogsForRCA(List<Log> logs, String scenario) {
        if (logs == null || logs.isEmpty()) {
            return "No logs available for analysis. Please generate logs first.";
        }

        String formattedLogs = formatLogs(logs);
        String prompt = buildRCAPrompt(formattedLogs, scenario);

        log.info("Calling HuggingFace API: {} with model: {}", apiUrl, model);

        try {
            RestClient client = RestClient.builder()
                    .baseUrl(apiUrl)
                    .build();

            // Build OpenAI-compatible request body (works for both HF Inference API & local TGI)
            Map<String, Object> message = new LinkedHashMap<>();
            message.put("role", "user");
            message.put("content", prompt);

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(message));
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);

            var requestSpec = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body);

            // Add auth header only if token is provided
            if (apiToken != null && !apiToken.isBlank() && !apiToken.startsWith("hf_REPLACE")) {
                requestSpec = client.post()
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(body);
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = requestSpec.retrieve().body(Map.class);

            return extractContent(response);

        } catch (RestClientException e) {
            log.error("Failed to call HuggingFace API: {}", e.getMessage());
            return buildFallbackRCA(logs, scenario, e.getMessage());
        } catch (Exception e) {
            log.error("Unexpected error calling HuggingFace API: {}", e.getMessage());
            return buildFallbackRCA(logs, scenario, e.getMessage());
        }
    }

    // ─────────────────────────────────────────────────────────
    // Prompt builder
    // ─────────────────────────────────────────────────────────
    private String buildRCAPrompt(String logs, String scenario) {
        return """
                You are a senior SRE (Site Reliability Engineer) expert specializing in distributed systems and incident management.
                
                Analyze the following application logs from the '%s' scenario and provide a comprehensive Root Cause Analysis (RCA).
                
                === APPLICATION LOGS ===
                %s
                === END LOGS ===
                
                Provide a structured RCA with exactly these sections:
                
                ## 🔍 Problem Summary
                Brief description of what happened and the overall impact.
                
                ## 🎯 Root Cause
                The specific technical root cause. Be precise - reference actual log messages, line patterns, and error codes you see.
                
                ## 📊 Impact Analysis
                - Which services were affected
                - Estimated user impact
                - Duration of the incident
                - Severity level (P1/P2/P3)
                
                ## ⏱️ Timeline
                Key events in chronological order with timestamps from the logs.
                
                ## 🛠️ Immediate Fix
                Step-by-step actions to resolve the issue right now.
                
                ## 🔒 Long-Term Prevention
                Engineering changes to prevent this type of incident in the future.
                
                ## 📈 Key Metrics to Monitor
                Specific metrics and thresholds that would have caught this earlier.
                
                Be specific. Reference actual log messages and error patterns you observed.
                """.formatted(scenario, logs);
    }

    // ─────────────────────────────────────────────────────────
    // Format logs for the prompt
    // ─────────────────────────────────────────────────────────
    private String formatLogs(List<Log> logs) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(logs.size(), 80); // cap to avoid token limits
        for (int i = 0; i < limit; i++) {
            Log l = logs.get(i);
            sb.append(String.format("[%s] [%s] [%s] %s",
                    l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "N/A",
                    l.getLogLevel(),
                    l.getService() != null ? l.getService().getServiceName() : "unknown",
                    l.getMessage()));
            if (l.getStatusCode() != null && l.getStatusCode() != 200) {
                sb.append(" | HTTP ").append(l.getStatusCode());
            }
            if (l.getLatencyMs() != null && l.getLatencyMs() > 0) {
                sb.append(" | latency=").append(l.getLatencyMs()).append("ms");
            }
            sb.append("\n");
        }
        if (logs.size() > limit) {
            sb.append("... and ").append(logs.size() - limit).append(" more log entries.\n");
        }
        return sb.toString();
    }

    // ─────────────────────────────────────────────────────────
    // Extract text from OpenAI-compatible response
    // ─────────────────────────────────────────────────────────
    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) return "Empty response from AI model.";
        try {
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices != null && !choices.isEmpty()) {
                Map<String, Object> choice = choices.get(0);
                Map<String, Object> message = (Map<String, Object>) choice.get("message");
                if (message != null) {
                    return (String) message.get("content");
                }
                // Some models return delta instead of message
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta != null) return (String) delta.get("content");
            }
            // Fallback: look for "generated_text" (older HF format)
            Object generated = response.get("generated_text");
            if (generated != null) return generated.toString();
        } catch (Exception e) {
            log.warn("Could not parse HF response: {}", e.getMessage());
        }
        return "Could not parse AI response. Raw response: " + response;
    }

    // ─────────────────────────────────────────────────────────
    // Fallback RCA when HF API is unavailable
    // ─────────────────────────────────────────────────────────
    private String buildFallbackRCA(List<Log> logs, String scenario, String error) {
        long errorCount = logs.stream().filter(l -> "ERROR".equals(l.getLogLevel())).count();
        long warnCount  = logs.stream().filter(l -> "WARN".equals(l.getLogLevel())).count();
        OptionalLong maxLatency = logs.stream()
                .filter(l -> l.getLatencyMs() != null)
                .mapToLong(Log::getLatencyMs).max();

        return """
                ⚠️ **HuggingFace API Unavailable** — Showing rule-based analysis instead.
                *Error: %s*
                
                ---
                
                ## 🔍 Problem Summary
                Scenario **%s** detected in application logs. Found **%d ERROR** and **%d WARN** events across %d total log entries.
                
                ## 🎯 Root Cause (Rule-Based)
                %s
                
                ## 📊 Impact Analysis
                - Total logs analysed: %d
                - Error events: %d (%.1f%%)
                - Warning events: %d
                - Peak latency observed: %s
                
                ## 🔒 To Enable AI-Powered RCA
                1. Get a free token at https://huggingface.co/settings/tokens
                2. Update `application.properties`:  
                   `huggingface.api-token=hf_your_token_here`
                3. Restart the application
                
                **Or run locally with Docker (no token needed):**
                ```
                docker run -p 8081:80 ghcr.io/huggingface/text-generation-inference:latest \\
                  --model-id mistralai/Mistral-7B-Instruct-v0.3
                ```
                Then set:  `huggingface.api-url=http://localhost:8081/v1/chat/completions`
                """.formatted(
                error,
                scenario,
                errorCount, warnCount, logs.size(),
                buildRuleBasedRootCause(scenario),
                logs.size(),
                errorCount, logs.isEmpty() ? 0 : (errorCount * 100.0 / logs.size()),
                warnCount,
                maxLatency.isPresent() ? maxLatency.getAsLong() + "ms" : "N/A"
        );
    }

    private String buildRuleBasedRootCause(String scenario) {
        return switch (scenario.toUpperCase()) {
            case "LATENCY_SPIKE" ->
                "High latency detected in upstream service causing cascading delays. Check connection pool exhaustion and retry configuration.";
            case "ERROR_STORM" ->
                "Sudden spike in error rate. Likely caused by a bad deployment, configuration change, or external dependency failure.";
            case "SERVICE_DOWN" ->
                "Service became completely unavailable. Possible OOM crash, pod eviction, or network partition. Check Kubernetes pod logs and node health.";
            case "DB_TIMEOUT" ->
                "Database connection pool exhausted. Slow queries causing lock contention. Check for missing indexes, long-running transactions, or DB resource limits.";
            case "MEMORY_LEAK" ->
                "Gradual heap growth detected. Unbounded cache or listener not being cleaned up. Check for objects held in static collections.";
            default -> "Analyse log patterns — look for the first ERROR entry and trace upstream dependencies.";
        };
    }
}


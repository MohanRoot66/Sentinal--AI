package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.net.InetSocketAddress;
import java.net.Proxy;
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

    @Value("${huggingface.connect-timeout:10000}")
    private int connectTimeout;

    @Value("${huggingface.read-timeout:120000}")
    private int readTimeout;

    @Value("${huggingface.proxy.host:}")
    private String proxyHost;

    @Value("${huggingface.proxy.port:0}")
    private int proxyPort;

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    /**
     * Send logs to HuggingFace model and return AI-generated RCA.
     */
    public String analyzeLogsForRCA(List<Log> logs, String scenario) {
        if (logs == null || logs.isEmpty()) {
            return "No logs available for analysis. Please generate logs first.";
        }

        boolean tokenConfigured = isTokenConfigured();
        if (!tokenConfigured) {
            log.warn("HuggingFace token not configured - returning rule-based analysis.");
            return buildFallbackRCA(logs, scenario,
                    "Token not set. Open application.properties → set huggingface.api-token=hf_your_token → restart app.");
        }

        String formattedLogs = formatLogs(logs);
        String prompt = buildRCAPrompt(formattedLogs, scenario);
        log.info("Calling HuggingFace API: {} | model: {} | logs: {}", apiUrl, model, logs.size());

        try {
            RestClient client = buildRestClient();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", maxTokens);
            body.put("temperature", temperature);
            body.put("stream", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractContent(response);

        } catch (RestClientException e) {
            String msg = e.getMessage() != null ? e.getMessage() : "Unknown error";
            log.error("HuggingFace API error: {}", msg);
            if (msg.contains("No such host") || msg.contains("UnknownHost") || msg.contains("connection")) {
                return buildFallbackRCA(logs, scenario,
                        "Cannot reach api-inference.huggingface.co — likely blocked by network/firewall.\n" +
                        "**Solutions:**\n" +
                        "1. Check internet connectivity\n" +
                        "2. If behind a corporate proxy, set in application.properties:\n" +
                        "   `huggingface.proxy.host=proxy.company.com`\n" +
                        "   `huggingface.proxy.port=8080`\n" +
                        "3. Or run Ollama locally (no internet needed):\n" +
                        "   `ollama run mistral` then set api-url=http://localhost:11434/v1/chat/completions");
            }
            return buildFallbackRCA(logs, scenario, msg);
        } catch (Exception e) {
            log.error("Unexpected error calling HuggingFace: {}", e.getMessage());
            return buildFallbackRCA(logs, scenario, e.getMessage());
        }
    }

    /**
     * Answer a chat question using HuggingFace, falls back to rule-based if unavailable.
     */
    public String answerQuestion(String question, String recentLogsContext) {
        if (!isTokenConfigured()) {
            return answerRuleBased(question);
        }

        String prompt = """
                You are a senior SRE AI assistant for Sentinel AI monitoring platform.
                
                Recent system context:
                %s
                
                User question: %s
                
                Answer concisely and technically. Reference the log context if relevant.
                """.formatted(recentLogsContext != null ? recentLogsContext : "No recent logs available.", question);

        try {
            RestClient client = buildRestClient();

            Map<String, Object> body = new LinkedHashMap<>();
            body.put("model", model);
            body.put("messages", List.of(Map.of("role", "user", "content", prompt)));
            body.put("max_tokens", 500);
            body.put("temperature", 0.5);
            body.put("stream", false);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(Map.class);

            return extractContent(response);
        } catch (Exception e) {
            log.warn("HuggingFace chat failed ({}), using rule-based fallback.", e.getMessage());
            return answerRuleBased(question);
        }
    }

    private RestClient buildRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(connectTimeout);
        factory.setReadTimeout(readTimeout);

        if (proxyHost != null && !proxyHost.isBlank() && proxyPort > 0) {
            log.info("Using HTTP proxy: {}:{}", proxyHost, proxyPort);
            factory.setProxy(new Proxy(Proxy.Type.HTTP, new InetSocketAddress(proxyHost, proxyPort)));
        }

        return RestClient.builder()
                .requestFactory(factory)
                .baseUrl(apiUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + apiToken)
                .build();
    }

    private boolean isTokenConfigured() {
        // "local" and "ollama" are valid tokens for local model servers
        if ("local".equals(apiToken) || "ollama".equals(apiToken)) return true;
        return apiToken != null && !apiToken.isBlank()
                && !apiToken.equals("hf_PASTE_YOUR_TOKEN_HERE")
                && !apiToken.startsWith("hf_REPLACE");
    }

    // ─────────────────────────────────────────────────────────
    // Prompt builder
    // ─────────────────────────────────────────────────────────
    private String buildRCAPrompt(String logs, String scenario) {
        return """
                You are a senior SRE expert specializing in distributed systems and incident management.
                
                Analyze the following application logs from the '%s' scenario and provide a comprehensive Root Cause Analysis (RCA).
                
                === APPLICATION LOGS ===
                %s
                === END LOGS ===
                
                Provide a structured RCA with exactly these sections:
                
                ## 🔍 Problem Summary
                ## 🎯 Root Cause
                ## 📊 Impact Analysis
                ## ⏱️ Timeline
                ## 🛠️ Immediate Fix
                ## 🔒 Long-Term Prevention
                ## 📈 Key Metrics to Monitor
                
                Be specific — reference actual log messages and error patterns you observed.
                """.formatted(scenario, logs);
    }

    // ─────────────────────────────────────────────────────────
    // Format logs for the prompt
    // ─────────────────────────────────────────────────────────
    private String formatLogs(List<Log> logs) {
        StringBuilder sb = new StringBuilder();
        int limit = Math.min(logs.size(), 80);
        for (int i = 0; i < limit; i++) {
            Log l = logs.get(i);
            sb.append(String.format("[%s] [%s] [%s] %s",
                    l.getTimestamp() != null ? l.getTimestamp().format(FMT) : "N/A",
                    l.getLogLevel(),
                    l.getService() != null ? l.getService().getServiceName() : "unknown",
                    l.getMessage()));
            if (l.getStatusCode() != null && l.getStatusCode() != 200)
                sb.append(" | HTTP ").append(l.getStatusCode());
            if (l.getLatencyMs() != null && l.getLatencyMs() > 0)
                sb.append(" | latency=").append(l.getLatencyMs()).append("ms");
            sb.append("\n");
        }
        if (logs.size() > limit)
            sb.append("... and ").append(logs.size() - limit).append(" more entries.\n");
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
                if (message != null) return (String) message.get("content");
                Map<String, Object> delta = (Map<String, Object>) choice.get("delta");
                if (delta != null) return (String) delta.get("content");
            }
            Object generated = response.get("generated_text");
            if (generated != null) return generated.toString();
        } catch (Exception e) {
            log.warn("Could not parse HF response: {}", e.getMessage());
        }
        return "Could not parse AI response: " + response;
    }

    // ─────────────────────────────────────────────────────────
    // Fallback RCA when HF API is unavailable
    // ─────────────────────────────────────────────────────────
    private String buildFallbackRCA(List<Log> logs, String scenario, String error) {
        long errorCount = logs.stream().filter(l -> "ERROR".equals(l.getLogLevel())).count();
        long warnCount  = logs.stream().filter(l -> "WARN".equals(l.getLogLevel())).count();
        OptionalLong maxLatency = logs.stream()
                .filter(l -> l.getLatencyMs() != null).mapToLong(Log::getLatencyMs).max();

        return """
                ⚠️ **HuggingFace API Unavailable** — Showing rule-based analysis instead.
                *Reason: %s*
                
                ---
                
                ## 🔍 Problem Summary
                Scenario **%s** — Found **%d ERRORs** and **%d WARNs** across %d log entries.
                
                ## 🎯 Root Cause (Rule-Based)
                %s
                
                ## 📊 Impact Analysis
                - Total logs: %d | Errors: %d (%.1f%%) | Warnings: %d
                - Peak latency: %s
                
                ## 🔒 To Enable Real AI Analysis
                
                **Option 1 — Fix network/proxy (if behind firewall):**
                Add to application.properties:
                ```
                huggingface.proxy.host=your.proxy.host
                huggingface.proxy.port=8080
                ```
                
                **Option 2 — Run Ollama locally (NO internet needed):**
                ```
                # Install from https://ollama.com then run:
                ollama run mistral
                ```
                Then in application.properties:
                ```
                huggingface.api-url=http://localhost:11434/v1/chat/completions
                huggingface.api-token=dummy
                huggingface.model=mistral
                ```
                """.formatted(
                error, scenario, errorCount, warnCount, logs.size(),
                buildRuleBasedRootCause(scenario),
                logs.size(), errorCount,
                logs.isEmpty() ? 0 : (errorCount * 100.0 / logs.size()),
                warnCount,
                maxLatency.isPresent() ? maxLatency.getAsLong() + "ms" : "N/A"
        );
    }

    private String buildRuleBasedRootCause(String scenario) {
        return switch (scenario.toUpperCase()) {
            case "LATENCY_SPIKE" -> "Upstream service slowdown caused cascading latency. Check connection pool exhaustion and retry amplification.";
            case "ERROR_STORM"   -> "Sudden error rate spike — likely a bad deployment or external dependency failure (SSL/config change).";
            case "SERVICE_DOWN"  -> "Service crash detected — OOM kill or pod eviction. Check JVM heap and Kubernetes events.";
            case "DB_TIMEOUT"    -> "DB connection pool exhausted. Slow queries causing lock contention. Check for missing indexes.";
            case "MEMORY_LEAK"   -> "Gradual heap growth — unbounded cache or unclosed resources. Check for static collections holding references.";
            default -> "Analyse the first ERROR log and trace the upstream dependency chain.";
        };
    }

    private String answerRuleBased(String question) {
        if (question == null) return "Please ask a question.";
        String q = question.toLowerCase();
        if (q.contains("latency") || q.contains("slow"))
            return "Latency spike detected — likely caused by upstream dependency slowdown (e.g. inventory-service). Check connection pool utilisation and retry configuration. Review logs for timeout patterns.";
        if (q.contains("error") || q.contains("failure"))
            return "Error rate elevated. Check recent deployments, SSL certificate changes, or external dependency failures. Review ERROR-level logs for the specific exception.";
        if (q.contains("root cause") || q.contains("rca"))
            return "Click 'Analyze with HuggingFace AI' on the RCA tab to get a full AI-generated root cause analysis of the current logs.";
        if (q.contains("fix") || q.contains("resolve") || q.contains("remediat"))
            return "Immediate steps: (1) Identify the first ERROR log entry, (2) Check which service is upstream of the failure, (3) Review recent deployments, (4) Consider rollback if a deploy correlates with the incident start.";
        if (q.contains("service") || q.contains("health"))
            return "All 4 services are monitored: order-api, inventory-service, payment-service, checkout-api. Check the Service Health panel on the left for current status.";
        return "Generate logs using the sidebar and click 'Analyze with HuggingFace AI' for a detailed AI analysis. I can answer questions about latency, errors, root cause, and remediation.";
    }
}

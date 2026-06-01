package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * ─────────────────────────────────────────────────────────────────────────────
 *  LOG ANALYSIS PIPELINE
 *
 *  The pipeline runs in 3 stages BEFORE any AI call is made:
 *
 *  Stage 1 – Rule-Based Anomaly Detection
 *      Applies deterministic rules on raw logs:
 *        • Error-rate threshold  (>10 % → anomaly)
 *        • Latency spike         (p95 > 1000ms OR any single log > 2000ms)
 *        • Error burst           (>3 ERRORs in a 60-second window)
 *        • Service-level pattern (single service owns >70 % of errors)
 *        • Cascading failure     (≥3 distinct services have errors)
 *
 *  Stage 2 – Evidence Extraction
 *      Picks the top "key evidence" logs that best represent each anomaly
 *      (max 20 logs total, prioritising ERROR > WARN, high latency).
 *
 *  Stage 3 – Structured Report Assembly
 *      Produces a compact, structured DetectionReport that is sent to Gemini
 *      instead of raw logs.  This means the AI gets:
 *        • A summary of what rules fired
 *        • Quantified metrics (error %, peak latency, burst timestamps)
 *        • Only the most relevant log lines (≤20)
 *      NOT 200 raw log lines — making the AI call faster, cheaper and more
 *      accurate.
 * ─────────────────────────────────────────────────────────────────────────────
 */
@Service
@Slf4j
public class LogAnalysisPipeline {

    // ── Thresholds ────────────────────────────────────────────────────────────
    private static final double ERROR_RATE_THRESHOLD    = 0.10;  // 10 %
    private static final long   LATENCY_P95_THRESHOLD   = 1000L; // ms
    private static final long   LATENCY_SINGLE_THRESH   = 2000L; // ms
    private static final int    ERROR_BURST_WINDOW_SECS = 60;
    private static final int    ERROR_BURST_MIN_COUNT   = 3;
    private static final double SERVICE_CONCENTRATION   = 0.70;  // 70 %
    private static final int    CASCADE_MIN_SERVICES    = 3;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Run the full pipeline on raw logs and return a DetectionReport.
     */
    public DetectionReport run(List<Log> rawLogs, String scenario) {
        log.info("[Pipeline] Stage 1 – Anomaly detection on {} raw logs", rawLogs.size());

        List<DetectedAnomaly> anomalies = new ArrayList<>();

        // ── Stage 1: Rule Evaluation ──────────────────────────────────────────

        long totalLogs = rawLogs.size();
        long errorCount = rawLogs.stream().filter(l -> "ERROR".equals(l.getLogLevel())).count();
        long warnCount  = rawLogs.stream().filter(l -> "WARN".equals(l.getLogLevel())).count();
        List<Long> latencies = rawLogs.stream()
                .filter(l -> l.getLatencyMs() != null && l.getLatencyMs() > 0)
                .map(Log::getLatencyMs).sorted().collect(Collectors.toList());

        double errorRate = totalLogs == 0 ? 0 : (double) errorCount / totalLogs;

        // Rule 1: Error Rate
        if (errorRate > ERROR_RATE_THRESHOLD) {
            anomalies.add(new DetectedAnomaly(
                "HIGH_ERROR_RATE",
                "CRITICAL",
                String.format("Error rate is %.1f%% (threshold: %.0f%%). %d errors across %d total log events.",
                        errorRate * 100, ERROR_RATE_THRESHOLD * 100, errorCount, totalLogs),
                0.95
            ));
        }

        // Rule 2: Latency Spike (p95)
        if (!latencies.isEmpty()) {
            long p95 = latencies.get((int)(latencies.size() * 0.95));
            long peakLatency = latencies.get(latencies.size() - 1);
            if (p95 > LATENCY_P95_THRESHOLD) {
                anomalies.add(new DetectedAnomaly(
                    "LATENCY_SPIKE",
                    peakLatency > 3000 ? "CRITICAL" : "HIGH",
                    String.format("p95 latency is %dms (threshold: %dms). Peak: %dms. Median: %dms.",
                            p95, LATENCY_P95_THRESHOLD, peakLatency, latencies.get(latencies.size() / 2)),
                    0.90
                ));
            }
            // Rule 3: Any single log has extreme latency
            if (peakLatency > LATENCY_SINGLE_THRESH && p95 <= LATENCY_P95_THRESHOLD) {
                anomalies.add(new DetectedAnomaly(
                    "LATENCY_OUTLIER",
                    "MEDIUM",
                    String.format("Isolated latency outlier detected: %dms. May indicate a slow downstream call or lock wait.", peakLatency),
                    0.65
                ));
            }
        }

        // Rule 4: Error Burst (multiple errors within a 60-second window)
        detectErrorBurst(rawLogs).ifPresent(anomalies::add);

        // Rule 5: Service Concentration (one service owns most errors)
        Map<String, Long> errorsByService = rawLogs.stream()
                .filter(l -> "ERROR".equals(l.getLogLevel()) && l.getService() != null)
                .collect(Collectors.groupingBy(l -> l.getService().getServiceName(), Collectors.counting()));

        if (!errorsByService.isEmpty() && errorCount > 0) {
            String hotService = Collections.max(errorsByService.entrySet(), Map.Entry.comparingByValue()).getKey();
            double concentration = (double) errorsByService.get(hotService) / errorCount;
            if (concentration >= SERVICE_CONCENTRATION) {
                anomalies.add(new DetectedAnomaly(
                    "SERVICE_HOT_SPOT",
                    "HIGH",
                    String.format("Service '%s' is the origin of %.0f%% of all errors (%d/%d). Likely root service.",
                            hotService, concentration * 100, errorsByService.get(hotService), errorCount),
                    0.88
                ));
            }
        }

        // Rule 6: Cascading Failure (errors spread across many services)
        long servicesWithErrors = errorsByService.size();
        if (servicesWithErrors >= CASCADE_MIN_SERVICES) {
            anomalies.add(new DetectedAnomaly(
                "CASCADING_FAILURE",
                "CRITICAL",
                String.format("Errors detected across %d distinct services: %s. Indicates upstream failure propagating downstream.",
                        servicesWithErrors, String.join(", ", errorsByService.keySet())),
                0.92
            ));
        }

        // Rule 7: High 5xx rate
        long http5xx = rawLogs.stream()
                .filter(l -> l.getStatusCode() != null && l.getStatusCode() >= 500).count();
        if (http5xx > 0) {
            anomalies.add(new DetectedAnomaly(
                "HTTP_5XX_ERRORS",
                http5xx > 5 ? "CRITICAL" : "HIGH",
                String.format("%d HTTP 5xx responses detected. Services are returning server errors to clients.", http5xx),
                0.85
            ));
        }

        log.info("[Pipeline] Stage 1 complete – {} anomalies detected: {}",
                anomalies.size(), anomalies.stream().map(a -> a.type).collect(Collectors.joining(", ")));

        // ── Stage 2: Evidence Extraction ──────────────────────────────────────
        log.info("[Pipeline] Stage 2 – Extracting key evidence logs");

        List<Log> evidence = extractKeyEvidence(rawLogs, anomalies);

        log.info("[Pipeline] Stage 2 complete – {} evidence logs selected from {} raw logs",
                evidence.size(), rawLogs.size());

        // ── Stage 3: Report Assembly ───────────────────────────────────────────
        log.info("[Pipeline] Stage 3 – Assembling structured detection report");

        DetectionReport report = new DetectionReport(
                scenario,
                totalLogs,
                errorCount,
                warnCount,
                latencies.isEmpty() ? 0 : latencies.get(latencies.size() - 1),
                latencies.isEmpty() ? 0 : latencies.get((int)(latencies.size() * 0.95)),
                errorRate,
                anomalies,
                evidence,
                errorsByService
        );

        log.info("[Pipeline] Complete – Report ready. Anomalies: {}, Evidence logs: {}",
                anomalies.size(), evidence.size());
        return report;
    }

    // ── Private Helpers ───────────────────────────────────────────────────────

    private Optional<DetectedAnomaly> detectErrorBurst(List<Log> logs) {
        List<Log> errorLogs = logs.stream()
                .filter(l -> "ERROR".equals(l.getLogLevel()) && l.getTimestamp() != null)
                .sorted(Comparator.comparing(Log::getTimestamp))
                .collect(Collectors.toList());

        for (int i = 0; i < errorLogs.size() - (ERROR_BURST_MIN_COUNT - 1); i++) {
            Log start = errorLogs.get(i);
            Log end   = errorLogs.get(i + ERROR_BURST_MIN_COUNT - 1);
            long secsDiff = java.time.Duration.between(start.getTimestamp(), end.getTimestamp()).getSeconds();
            if (secsDiff <= ERROR_BURST_WINDOW_SECS) {
                return Optional.of(new DetectedAnomaly(
                    "ERROR_BURST",
                    "HIGH",
                    String.format("%d errors occurred within a %d-second window starting at %s. Indicative of a rapid failure cascade.",
                            ERROR_BURST_MIN_COUNT, ERROR_BURST_WINDOW_SECS,
                            start.getTimestamp().toString()),
                    0.82
                ));
            }
        }
        return Optional.empty();
    }

    private List<Log> extractKeyEvidence(List<Log> rawLogs, List<DetectedAnomaly> anomalies) {
        Set<String> anomalyTypes = anomalies.stream().map(a -> a.type).collect(Collectors.toSet());
        List<Log> evidence = new ArrayList<>();

        // Always include all ERROR logs (up to 10)
        rawLogs.stream()
                .filter(l -> "ERROR".equals(l.getLogLevel()))
                .limit(10)
                .forEach(evidence::add);

        // Include high-latency logs if latency anomaly detected
        if (anomalyTypes.contains("LATENCY_SPIKE") || anomalyTypes.contains("LATENCY_OUTLIER")) {
            rawLogs.stream()
                    .filter(l -> l.getLatencyMs() != null && l.getLatencyMs() > LATENCY_P95_THRESHOLD)
                    .sorted(Comparator.comparingLong((Log l) -> l.getLatencyMs()).reversed())
                    .limit(5)
                    .filter(l -> !evidence.contains(l))
                    .forEach(evidence::add);
        }

        // Include WARN logs that aren't already covered (up to 5)
        rawLogs.stream()
                .filter(l -> "WARN".equals(l.getLogLevel()))
                .limit(5)
                .filter(l -> !evidence.contains(l))
                .forEach(evidence::add);

        // Fill remaining slots with high-status-code logs
        rawLogs.stream()
                .filter(l -> l.getStatusCode() != null && l.getStatusCode() >= 500)
                .filter(l -> !evidence.contains(l))
                .limit(20 - evidence.size())
                .forEach(evidence::add);

        return evidence;
    }

    // ── Data classes ──────────────────────────────────────────────────────────

    public record DetectedAnomaly(
            String type,
            String severity,
            String description,
            double confidenceScore
    ) {}

    public record DetectionReport(
            String scenario,
            long totalLogs,
            long errorCount,
            long warnCount,
            long peakLatencyMs,
            long p95LatencyMs,
            double errorRate,
            List<DetectedAnomaly> detectedAnomalies,
            List<Log> keyEvidenceLogs,
            Map<String, Long> errorsByService
    ) {
        public boolean hasAnomalies() {
            return !detectedAnomalies.isEmpty();
        }

        public String highestSeverity() {
            if (detectedAnomalies.stream().anyMatch(a -> "CRITICAL".equals(a.severity()))) return "CRITICAL";
            if (detectedAnomalies.stream().anyMatch(a -> "HIGH".equals(a.severity()))) return "HIGH";
            if (detectedAnomalies.stream().anyMatch(a -> "MEDIUM".equals(a.severity()))) return "MEDIUM";
            return "LOW";
        }
    }
}


package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.models.Metric;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.repositories.MetricRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class AIReasoningService {
    private final MetricRepository metricRepository;
    private final LogRepository logRepository;

    /**
     * Generate AI-driven root cause explanation based on incident data
     */
    public String explainAnomaly(String serviceId, String anomalyType,
                                Double anomalyScore, String affectedMetric) {
        StringBuilder explanation = new StringBuilder();

        LocalDateTime oneHourAgo = LocalDateTime.now().minusHours(1);

        List<Log> recentErrors = logRepository.findErrorAndWarnLogsByService(serviceId);
        List<Metric> recentMetrics = metricRepository.findLatestMetricsByService(serviceId);

        explanation.append(String.format("%s detected with confidence score %.2f%% on service %s.\n",
            anomalyType, anomalyScore * 100, serviceId));

        if (anomalyType.equals("LATENCY_SPIKE")) {
            explanation.append("API latency increased significantly. ");
            if (!recentErrors.isEmpty()) {
                explanation.append("Error logs indicate: ");
                recentErrors.stream().limit(3).forEach(log ->
                    explanation.append(log.getMessage()).append(". "));
            }
        } else if (anomalyType.equals("RETRY_STORM")) {
            explanation.append("Retry storm detected indicating downstream service degradation. ");
            explanation.append("This typically indicates a dependency timeout or resource exhaustion.");
        } else if (anomalyType.equals("ERROR_RATE_SPIKE")) {
            explanation.append("Error rate increased abnormally. ");
            explanation.append("Check recent deployments and service logs for failures.");
        } else if (anomalyType.equals("TRAFFIC_ANOMALY")) {
            explanation.append("Unusual traffic pattern detected. ");
            explanation.append("Monitor for potential DDoS or legitimate traffic surge.");
        }

        return explanation.toString();
    }

    /**
     * Answer natural language questions about system behavior
     */
    public String answerQuestion(String question) {
        question = question.toLowerCase();

        if (question.contains("latency") && question.contains("increase")) {
            return "Latency increased by 37% following deployment v2.3. The inventory service showed elevated " +
                    "response times, causing retry bursts in order-processing APIs. This degradation directly " +
                    "impacts checkout latency.";
        } else if (question.contains("which") && question.contains("deployment")) {
            return "Deployment v2.3 occurred 15 minutes before the anomaly detection. This deployment " +
                    "modified the inventory-service dependency retry logic, which triggered cascading failures.";
        } else if (question.contains("which") && question.contains("user")) {
            return "Primarily checkout flow users are impacted. Order APIs show 23% elevated latency. " +
                    "Estimated user impact: 15,000 concurrent users experiencing slowness.";
        } else if (question.contains("investigate")) {
            return "Priority investigation steps: (1) Check inventory-service response times, (2) Review " +
                    "deployment v2.3 rollback feasibility, (3) Analyze retry configuration, (4) Monitor " +
                    "queue buildup metrics.";
        } else if (question.contains("root cause")) {
            return "Root cause: Inventory service dependency slow response → Order API retries increase → " +
                    "Thread pool exhaustion → Cascading latency increase → Customer-facing slowness.";
        }

        return "Unable to determine specific answer. Please provide more context about the issue.";
    }

    /**
     * Generate incident summary
     */
    public Map<String, Object> generateIncidentSummary(String serviceId, String anomalyType,
                                                       Double anomalyScore) {
        Map<String, Object> summary = new HashMap<>();
        summary.put("incidentTitle", String.format("%s Alert - %s", anomalyType, serviceId));
        summary.put("severity", calculateSeverity(anomalyScore));
        summary.put("detectedAt", LocalDateTime.now().toString());
        summary.put("rootCauseExplanation", explainAnomaly(serviceId, anomalyType, anomalyScore, "metric"));
        summary.put("affectedSystems", new String[]{serviceId});
        summary.put("recommendedActions", new String[]{
            "Acknowledge incident in AlertManager",
            "Review recent deployments",
            "Monitor dependent services",
            "Check service logs for errors",
            "Consider rollback if necessary"
        });
        return summary;
    }

    private String calculateSeverity(Double anomalyScore) {
        if (anomalyScore >= 0.9) return "CRITICAL";
        if (anomalyScore >= 0.7) return "HIGH";
        if (anomalyScore >= 0.5) return "MEDIUM";
        return "LOW";
    }
}


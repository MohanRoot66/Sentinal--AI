package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.repositories.ServiceRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.*;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
@Slf4j
public class LogGeneratorService {

    private final LogRepository logRepository;
    private final ServiceRepository serviceRepository;
    private final Random random = new Random();

    public enum Scenario {
        NORMAL,
        LATENCY_SPIKE,
        ERROR_STORM,
        SERVICE_DOWN,
        DB_TIMEOUT,
        MEMORY_LEAK
    }

    /**
     * Generate logs for the given scenario and persist to DB.
     * Returns the list of generated logs.
     */
    public List<Log> generateLogs(Scenario scenario) {
        List<Service> services = serviceRepository.findAll();
        if (services.isEmpty()) {
            throw new RuntimeException("No services found. DataInitializer may not have run.");
        }

        // Clear old generated logs so we always start fresh
        logRepository.deleteAll();

        List<Log> generated = new ArrayList<>();

        switch (scenario) {
            case NORMAL        -> generated = generateNormalLogs(services);
            case LATENCY_SPIKE -> generated = generateLatencySpikeLogs(services);
            case ERROR_STORM   -> generated = generateErrorStormLogs(services);
            case SERVICE_DOWN  -> generated = generateServiceDownLogs(services);
            case DB_TIMEOUT    -> generated = generateDbTimeoutLogs(services);
            case MEMORY_LEAK   -> generated = generateMemoryLeakLogs(services);
        }

        List<Log> saved = logRepository.saveAll(generated);
        log.info("Generated {} logs for scenario: {}", saved.size(), scenario);
        return saved;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: NORMAL – healthy traffic
    // ─────────────────────────────────────────────────────────
    private List<Log> generateNormalLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(30);
        Service orderApi = findService(services, "order-api");
        Service inventory = findService(services, "inventory-service");
        Service payment  = findService(services, "payment-service");
        Service checkout = findService(services, "checkout-api");

        String[] users = {"user_101", "user_202", "user_303", "user_404", "user_505"};
        for (int i = 0; i < 40; i++) {
            String userId = users[i % users.length];
            String traceId = "trace_" + (1000 + i);
            LocalDateTime ts = base.plusSeconds(i * 45L);

            logs.add(log(orderApi,   "INFO",  ts,           "Order received for userId=" + userId, 200, 30L + random.nextInt(20), traceId, userId));
            logs.add(log(inventory,  "INFO",  ts.plusSeconds(1), "Inventory check passed for order=" + (i + 1), 200, 15L + random.nextInt(10), traceId, null));
            logs.add(log(payment,    "INFO",  ts.plusSeconds(2), "Payment authorised for userId=" + userId + " amount=$" + (50 + random.nextInt(200)), 200, 45L + random.nextInt(20), traceId, userId));
            logs.add(log(checkout,   "INFO",  ts.plusSeconds(3), "Checkout complete for userId=" + userId, 200, 20L + random.nextInt(10), traceId, userId));
        }
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: LATENCY SPIKE – inventory slows → cascades
    // ─────────────────────────────────────────────────────────
    private List<Log> generateLatencySpikeLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(20);
        Service orderApi = findService(services, "order-api");
        Service inventory = findService(services, "inventory-service");
        Service checkout  = findService(services, "checkout-api");

        // Phase 1: Normal (first 10 mins)
        for (int i = 0; i < 15; i++) {
            String traceId = "trace_pre_" + i;
            LocalDateTime ts = base.plusSeconds(i * 40L);
            logs.add(log(orderApi,  "INFO", ts,                "Order processed successfully - latency normal", 200, 45L + random.nextInt(10), traceId, "user_" + i));
            logs.add(log(inventory, "INFO", ts.plusSeconds(1), "Stock check completed in " + (20 + random.nextInt(10)) + "ms", 200, 20L + random.nextInt(10), traceId, null));
        }

        // Phase 2: Deployment event
        LocalDateTime deployTime = base.plusMinutes(10);
        logs.add(log(inventory, "WARN",  deployTime,              "Deployment v2.3 started on inventory-service", 200, 0L, "trace_deploy", null));
        logs.add(log(inventory, "INFO",  deployTime.plusSeconds(5),"Deployment v2.3 completed. Retry policy changed: maxRetries=5 timeout=8000ms", 200, 0L, "trace_deploy", null));

        // Phase 3: Latency spike emerges
        for (int i = 0; i < 20; i++) {
            String traceId = "trace_spike_" + i;
            LocalDateTime ts = deployTime.plusSeconds(10 + i * 30L);
            long invLatency = 500L + i * 150 + random.nextInt(100);  // grows 500ms → 3500ms
            long orderLatency = invLatency + 200;

            logs.add(log(inventory, invLatency > 2000 ? "ERROR" : "WARN", ts,
                    "Inventory response slow: " + invLatency + "ms (threshold: 200ms). Connection pool: " + (60 + i * 2) + "% utilised",
                    invLatency > 2000 ? 504 : 200, invLatency, traceId, null));

            if (i > 2) {
                logs.add(log(orderApi, "WARN", ts.plusSeconds(1),
                        "Retry attempt " + Math.min(i / 3, 5) + "/5 for inventory-service call. Upstream timeout detected.",
                        503, 100L, traceId, "user_" + i));
            }
            logs.add(log(orderApi, orderLatency > 3000 ? "ERROR" : "WARN", ts.plusSeconds(2),
                    "Order API latency elevated: " + orderLatency + "ms. SLA threshold: 500ms",
                    orderLatency > 3000 ? 503 : 200, orderLatency, traceId, "user_" + i));
            logs.add(log(checkout, "WARN", ts.plusSeconds(3),
                    "Checkout degraded - upstream order-api slow (" + orderLatency + "ms). User experience impacted.",
                    200, orderLatency + 50, traceId, "user_" + i));
        }

        // Phase 4: Thread pool exhaustion
        LocalDateTime exhaustion = deployTime.plusMinutes(8);
        logs.add(log(orderApi, "ERROR", exhaustion,              "CRITICAL: Thread pool exhausted! Active threads: 200/200. Queue depth: 847", 503, 0L, "trace_exhaust", null));
        logs.add(log(orderApi, "ERROR", exhaustion.plusSeconds(5), "RejectedExecutionException: Task queue full. Dropping requests.", 503, 0L, "trace_exhaust", null));
        logs.add(log(inventory, "ERROR", exhaustion.plusSeconds(10), "Connection pool timeout after 8000ms. Database connections: 100/100 active.", 504, 8000L, "trace_exhaust", null));
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: ERROR STORM – high error rate spike
    // ─────────────────────────────────────────────────────────
    private List<Log> generateErrorStormLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(15);
        Service orderApi = findService(services, "order-api");
        Service payment  = findService(services, "payment-service");
        Service checkout = findService(services, "checkout-api");

        // Normal baseline
        for (int i = 0; i < 10; i++) {
            String traceId = "trace_ok_" + i;
            LocalDateTime ts = base.plusSeconds(i * 30L);
            logs.add(log(orderApi, "INFO", ts, "Order completed successfully", 200, 50L, traceId, "user_" + i));
        }

        // Bad deployment / config change
        logs.add(log(payment, "WARN", base.plusMinutes(5), "Config reload: payment-gateway-url changed to https://new-gateway.payments.io", 200, 0L, "trace_config", null));
        logs.add(log(payment, "ERROR", base.plusMinutes(5).plusSeconds(2), "SSL certificate mismatch for new-gateway.payments.io - PKIX path validation failed", 502, 1200L, "trace_config", null));

        // Error storm begins
        String[] errorMessages = {
            "Payment gateway connection refused: ECONNREFUSED new-gateway.payments.io:443",
            "Payment transaction failed: javax.net.ssl.SSLHandshakeException: Certificate not trusted",
            "NullPointerException at PaymentProcessor.process(PaymentProcessor.java:147) - gateway response null",
            "Payment timeout after 30000ms - gateway not responding",
            "HTTP 502 Bad Gateway from payment-gateway. Retrying... attempt %d/3",
            "Order rollback triggered: payment failure for orderId=%d",
            "Database transaction rolled back: payment_transactions table. Constraint violation."
        };

        for (int i = 0; i < 30; i++) {
            String traceId = "trace_err_" + i;
            LocalDateTime ts = base.plusMinutes(5).plusSeconds(i * 20L);
            String msg = errorMessages[i % errorMessages.length]
                    .replace("%d", String.valueOf(i + 1));
            logs.add(log(payment,  "ERROR", ts,               msg, 502, 1000L + random.nextInt(2000), traceId, "user_" + i));
            logs.add(log(orderApi, "ERROR", ts.plusSeconds(1), "Order " + (5000 + i) + " failed: payment service error. Customer notified.", 500, 100L, traceId, "user_" + i));
            logs.add(log(checkout, "ERROR", ts.plusSeconds(2), "Checkout failed for user_" + i + " - payment declined unexpectedly. Error rate: " + (30 + i) + "%", 500, 50L, traceId, "user_" + i));
        }
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: SERVICE DOWN – complete outage
    // ─────────────────────────────────────────────────────────
    private List<Log> generateServiceDownLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(25);
        Service inventory = findService(services, "inventory-service");
        Service orderApi  = findService(services, "order-api");
        Service checkout  = findService(services, "checkout-api");

        // Normal baseline
        for (int i = 0; i < 8; i++) {
            logs.add(log(inventory, "INFO", base.plusSeconds(i * 60L), "Inventory service healthy. Items checked: " + (100 + i * 12), 200, 25L, "trace_ok_" + i, null));
        }

        // OOM / crash sequence
        LocalDateTime crashTime = base.plusMinutes(8);
        logs.add(log(inventory, "ERROR", crashTime,               "SEVERE: OutOfMemoryError: Java heap space. Heap usage: 98%", 500, 0L, "trace_crash", null));
        logs.add(log(inventory, "ERROR", crashTime.plusSeconds(2), "JVM crash detected. Process exiting with code 137 (OOM Killer)", 500, 0L, "trace_crash", null));
        logs.add(log(inventory, "ERROR", crashTime.plusSeconds(5), "Health check FAILED: inventory-service not responding on :8082/actuator/health", 503, 0L, "trace_crash", null));

        // Downstream impact
        for (int i = 0; i < 25; i++) {
            String traceId = "trace_down_" + i;
            LocalDateTime ts = crashTime.plusSeconds(10 + i * 25L);
            logs.add(log(orderApi, "ERROR", ts, "Upstream dependency UNAVAILABLE: inventory-service. CircuitBreaker state: OPEN. Requests failing fast.", 503, 5L, traceId, "user_" + i));
            logs.add(log(checkout, "ERROR", ts.plusSeconds(1), "Cannot complete checkout: inventory unavailable. Returning 503 to " + (3000 + i) + " waiting users.", 503, 5L, traceId, "user_" + i));
            if (i % 5 == 0) {
                logs.add(log(orderApi, "WARN", ts.plusSeconds(2), "CircuitBreaker fallback activated. Serving cached inventory data (may be stale).", 200, 10L, traceId, null));
            }
        }

        // Recovery attempt
        LocalDateTime recoveryTime = crashTime.plusMinutes(12);
        logs.add(log(inventory, "INFO", recoveryTime,               "Kubernetes pod restarting inventory-service (attempt 3/5)", 200, 0L, "trace_recovery", null));
        logs.add(log(inventory, "WARN", recoveryTime.plusSeconds(30), "inventory-service started but health check DEGRADED: heap 85%", 200, 0L, "trace_recovery", null));
        logs.add(log(inventory, "INFO", recoveryTime.plusSeconds(60), "inventory-service health check PASSING. CircuitBreaker transitioning: OPEN → HALF_OPEN", 200, 0L, "trace_recovery", null));
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: DB TIMEOUT – database connection issues
    // ─────────────────────────────────────────────────────────
    private List<Log> generateDbTimeoutLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(20);
        Service orderApi = findService(services, "order-api");
        Service payment  = findService(services, "payment-service");

        // Normal
        for (int i = 0; i < 10; i++) {
            logs.add(log(orderApi, "INFO", base.plusSeconds(i * 45L), "DB query executed in " + (5 + random.nextInt(20)) + "ms. Orders table rows: " + (50000 + i * 100), 200, (long)(5 + random.nextInt(20)), "trace_db_ok_" + i, null));
        }

        // DB issue starts
        LocalDateTime dbIssueStart = base.plusMinutes(8);
        logs.add(log(orderApi, "WARN",  dbIssueStart,               "DB connection pool: 45/50 connections active. Latency p99: 890ms", 200, 890L, "trace_db_warn", null));
        logs.add(log(orderApi, "WARN",  dbIssueStart.plusSeconds(30),"DB connection pool: 50/50 connections active. New requests waiting for connection.", 200, 0L, "trace_db_warn", null));
        logs.add(log(orderApi, "ERROR", dbIssueStart.plusMinutes(1), "HikariPool-1 - Connection is not available, request timed out after 30000ms.", 500, 30000L, "trace_db_err", null));

        for (int i = 0; i < 20; i++) {
            String traceId = "trace_db_" + i;
            LocalDateTime ts = dbIssueStart.plusMinutes(1).plusSeconds(i * 20L);
            long queryTime = 5000L + i * 500 + random.nextInt(1000);
            logs.add(log(orderApi, "ERROR", ts,
                    "QueryTimeoutException: SELECT * FROM orders WHERE status='PENDING' exceeded " + queryTime + "ms. Lock contention detected on orders table.",
                    500, queryTime, traceId, null));
            logs.add(log(payment, "ERROR", ts.plusSeconds(2),
                    "Payment transaction failed: Cannot acquire DB lock on payment_transactions. Deadlock victim. TxID=" + (9000 + i),
                    500, queryTime, traceId, "user_" + i));
            if (i % 4 == 0) {
                logs.add(log(orderApi, "WARN", ts.plusSeconds(3),
                        "Slow query detected: UPDATE orders SET status='PROCESSING' took " + (queryTime + 1000) + "ms. Blocking " + (10 + i * 2) + " other transactions.",
                        200, queryTime + 1000, traceId, null));
            }
        }
        logs.add(log(orderApi, "ERROR", dbIssueStart.plusMinutes(8), "FATAL: Too many connections. MySQL max_connections=151 reached. Refusing new connections.", 500, 0L, "trace_db_fatal", null));
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // SCENARIO: MEMORY LEAK – gradual heap growth
    // ─────────────────────────────────────────────────────────
    private List<Log> generateMemoryLeakLogs(List<Service> services) {
        List<Log> logs = new ArrayList<>();
        LocalDateTime base = LocalDateTime.now().minusMinutes(40);
        Service orderApi  = findService(services, "order-api");
        Service inventory = findService(services, "inventory-service");

        for (int i = 0; i < 35; i++) {
            LocalDateTime ts = base.plusMinutes(i);
            int heapPct    = 20 + i * 2;   // 20% → 90%
            long latency   = 40L + i * 15; // slowly rising latency
            String level   = heapPct > 80 ? "ERROR" : heapPct > 60 ? "WARN" : "INFO";

            logs.add(log(orderApi, level, ts,
                    "JVM Heap usage: " + heapPct + "%. GC time: " + (i * 5) + "ms/min. Active sessions: " + (200 + i * 10) + ". Cached objects: " + (50000 + i * 3000),
                    200, latency, "trace_mem_" + i, null));

            if (i > 10 && i % 3 == 0) {
                logs.add(log(orderApi, "WARN", ts.plusSeconds(30),
                        "Full GC triggered. Duration: " + (500 + i * 50) + "ms. Stop-the-world pause impacting response times.",
                        200, (long)(500 + i * 50), "trace_gc_" + i, null));
            }
            if (i > 20) {
                logs.add(log(inventory, "WARN", ts.plusSeconds(45),
                        "Cache size growing unbounded: " + (100 + (i - 20) * 50) + "MB. WeakReference cache eviction not occurring. Possible memory leak in CacheManager.putEntry()",
                        200, latency + 100, "trace_cache_" + i, null));
            }
            if (heapPct >= 85) {
                logs.add(log(orderApi, "ERROR", ts.plusSeconds(50),
                        "CRITICAL: Heap at " + heapPct + "%. GC overhead limit exceeded. Application may become unresponsive.",
                        500, latency * 3, "trace_oom_" + i, null));
            }
        }
        return logs;
    }

    // ─────────────────────────────────────────────────────────
    // Helpers
    // ─────────────────────────────────────────────────────────
    private Log log(Service service, String level, LocalDateTime ts, String message,
                    int statusCode, long latencyMs, String traceId, String userId) {
        return Log.builder()
                .service(service)
                .logLevel(level)
                .timestamp(ts)
                .message(message)
                .statusCode(statusCode)
                .latencyMs(latencyMs)
                .traceId(traceId)
                .userId(userId)
                .requestId("req_" + UUID.randomUUID().toString().substring(0, 8))
                .build();
    }

    private Service findService(List<Service> services, String name) {
        return services.stream()
                .filter(s -> s.getServiceName().equals(name))
                .findFirst()
                .orElse(services.get(0)); // fallback to first service
    }
}


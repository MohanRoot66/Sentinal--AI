package com.example.sentinalAI.config;

import com.example.sentinalAI.models.Metric;
import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.repositories.MetricRepository;
import com.example.sentinalAI.repositories.ServiceRepository;
import com.example.sentinalAI.services.LogGeneratorService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.LocalDateTime;
import java.util.Random;

@Configuration
@RequiredArgsConstructor
@Slf4j
public class DataInitializer {

    private final LogGeneratorService logGeneratorService;

    @Bean
    public CommandLineRunner initializeData(
            ServiceRepository serviceRepository,
            MetricRepository metricRepository) {

        return args -> {
            // Create services
            Service orderAPI = Service.builder()
                    .serviceName("order-api")
                    .serviceType("API")
                    .description("Order processing API")
                    .isHealthy(true)
                    .healthStatus("HEALTHY")
                    .lastChecked(LocalDateTime.now())
                    .build();

            Service inventoryService = Service.builder()
                    .serviceName("inventory-service")
                    .serviceType("Service")
                    .description("Inventory management service")
                    .isHealthy(true)
                    .healthStatus("HEALTHY")
                    .lastChecked(LocalDateTime.now())
                    .build();

            Service paymentService = Service.builder()
                    .serviceName("payment-service")
                    .serviceType("Service")
                    .description("Payment processing service")
                    .isHealthy(true)
                    .healthStatus("HEALTHY")
                    .lastChecked(LocalDateTime.now())
                    .build();

            Service checkoutAPI = Service.builder()
                    .serviceName("checkout-api")
                    .serviceType("API")
                    .description("Checkout API")
                    .isHealthy(true)
                    .healthStatus("HEALTHY")
                    .lastChecked(LocalDateTime.now())
                    .build();

            serviceRepository.save(orderAPI);
            serviceRepository.save(inventoryService);
            serviceRepository.save(paymentService);
            serviceRepository.save(checkoutAPI);

            // Generate demo metrics
            generateNormalMetrics(metricRepository, orderAPI, inventoryService, checkoutAPI);
            generateAnomalyMetrics(metricRepository, orderAPI, inventoryService);

            // ── Auto-generate LATENCY_SPIKE logs so the dashboard is populated immediately ──
            log.info("Auto-generating LATENCY_SPIKE logs on startup...");
            logGeneratorService.generateLogs(LogGeneratorService.Scenario.LATENCY_SPIKE);
            log.info("Startup log generation complete. Dashboard is ready.");
        };
    }

    private void generateNormalMetrics(MetricRepository metricRepository,
                                       Service orderAPI, Service inventoryService,
                                       Service checkoutAPI) {
        // Normal latency metrics (50ms average)
        for (int i = 0; i < 10; i++) {
            Metric latencyMetric = Metric.builder()
                    .service(orderAPI)
                    .metricName("latency")
                    .metricValue(50.0 + new Random().nextDouble() * 10)
                    .unit("ms")
                    .timestamp(LocalDateTime.now().minusMinutes(20 - i * 2))
                    .isAnomaly(false)
                    .anomalyScore(0.0)
                    .build();
            metricRepository.save(latencyMetric);

            Metric errorRateMetric = Metric.builder()
                    .service(orderAPI)
                    .metricName("error_rate")
                    .metricValue(0.5 + new Random().nextDouble() * 0.5)
                    .unit("%")
                    .timestamp(LocalDateTime.now().minusMinutes(20 - i * 2))
                    .isAnomaly(false)
                    .anomalyScore(0.0)
                    .build();
            metricRepository.save(errorRateMetric);
        }
    }

    private void generateAnomalyMetrics(MetricRepository metricRepository,
                                        Service orderAPI, Service inventoryService) {
        LocalDateTime anomalyStart = LocalDateTime.now().minusMinutes(5);

        // Simulated latency spike (37% increase as per demo narrative)
        for (int i = 0; i < 5; i++) {
            Metric anomalyMetric = Metric.builder()
                    .service(orderAPI)
                    .metricName("latency")
                    .metricValue(85.0 + new Random().nextDouble() * 20) // 37% spike from 50ms baseline
                    .unit("ms")
                    .timestamp(anomalyStart.plusMinutes(i))
                    .isAnomaly(true)
                    .anomalyScore(0.87)
                    .build();
            metricRepository.save(anomalyMetric);
        }

        // Inventory service latency increase
        for (int i = 0; i < 5; i++) {
            Metric inventoryLatency = Metric.builder()
                    .service(inventoryService)
                    .metricName("latency")
                    .metricValue(200.0 + new Random().nextDouble() * 50)
                    .unit("ms")
                    .timestamp(anomalyStart.plusMinutes(i))
                    .isAnomaly(true)
                    .anomalyScore(0.82)
                    .build();
            metricRepository.save(inventoryLatency);
        }

        // Retry storm metric
        for (int i = 0; i < 5; i++) {
            Metric retryMetric = Metric.builder()
                    .service(orderAPI)
                    .metricName("retry_rate")
                    .metricValue(15.0 + new Random().nextDouble() * 10) // Retry amplification
                    .unit("retries/sec")
                    .timestamp(anomalyStart.plusMinutes(i))
                    .isAnomaly(true)
                    .anomalyScore(0.79)
                    .build();
            metricRepository.save(retryMetric);
        }
    }

    private void generateDemoLogs(Service orderAPI, Service inventoryService,
                                  Service checkoutAPI) {
        // Replaced by LogGeneratorService - logs are now auto-generated on startup
    }
}


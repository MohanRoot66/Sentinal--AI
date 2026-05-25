# Sentinel AI - POC Demo Implementation

## Overview

This is a complete implementation of the Sentinel AI Platform POC, demonstrating AI-powered silent failure detection and operational intelligence. The application includes:

- **REST APIs** for metrics, logs, and service ingestion
- **Anomaly Detection** with AI reasoning capabilities
- **Interactive Dashboard** showcasing the demo scenario
- **AI Chat Interface** for natural language incident queries
- **Swagger/OpenAPI** documentation for all endpoints
- **Pre-loaded Demo Data** simulating a realistic production incident

## Demo Scenario

### The Story
A payment/order processing system experiences degradation:
1. **Deployment v2.3** is released on inventory-service
2. **Inventory service** begins responding slowly (>5000ms)
3. **Retry storms** triggered in order-api due to timeout handling
4. **Order API latency** increases by 37% (50ms → 87ms)
5. **Checkout flow** users experience slowness
6. **Traditional alerts** don't immediately trigger on latency (+37% from 50ms baseline)
7. **Sentinel AI** detects and explains the entire chain before customer impact escalates

### Key Demonstration Points
- ✅ **Early Detection**: Anomaly detected before traditional thresholds triggered
- ✅ **AI Correlation**: Automatically links inventory issue to checkout latency
- ✅ **Root Cause**: Explains deployment timing and retry mechanism failure
- ✅ **Business Impact**: Quantifies user impact (15,000 concurrent users)
- ✅ **Reduced MTTR**: Provides instant explanation vs. 45+ minute manual debugging

## Project Structure

```
sentinalAI/
├── src/main/java/com/example/sentinalAI/
│   ├── controllers/
│   │   ├── MetricController.java       # Metrics ingestion & retrieval
│   │   ├── LogController.java          # Log ingestion & querying
│   │   ├── ServiceController.java      # Service registration & health
│   │   └── AIController.java           # AI reasoning & chat
│   ├── services/
│   │   ├── MetricService.java          # Metric business logic
│   │   ├── LogService.java             # Log business logic
│   │   ├── ServiceManagementService.java
│   │   ├── AnomalyDetectionService.java
│   │   └── AIReasoningService.java     # AI explanations & answers
│   ├── models/
│   │   ├── BaseEntity.java
│   │   ├── Service.java
│   │   ├── Metric.java
│   │   ├── Log.java
│   │   ├── Incident.java
│   │   └── Anomaly.java
│   ├── repositories/
│   │   ├── ServiceRepository.java
│   │   ├── MetricRepository.java
│   │   ├── LogRepository.java
│   │   └── IncidentRepository.java
│   ├── dto/
│   │   ├── MetricDTO.java
│   │   ├── LogDTO.java
│   │   ├── AnomalyDTO.java
│   │   └── ChatMessageDTO.java
│   ├── config/
│   │   ├── OpenAPIConfig.java
│   │   └── DataInitializer.java        # Demo data generation
│   └── SentinalAiApplication.java
├── src/main/resources/
│   ├── application.properties           # Configuration
│   └── static/
│       └── index.html                  # Interactive dashboard
└── pom.xml                              # Maven dependencies
```

## Technology Stack

- **Framework**: Spring Boot 4.0.6
- **Language**: Java 17
- **Database**: PostgreSQL (configured)
- **Search**: Elasticsearch (optional)
- **Messaging**: Kafka (optional)
- **API Docs**: SpringDoc OpenAPI (Swagger)
- **Frontend**: HTML5 + Vanilla JavaScript
- **Build**: Maven

## Getting Started

### Prerequisites
- Java 17+
- Maven 3.8+
- PostgreSQL 13+ (or modify application.properties for H2 in-memory DB)

### Option 1: Run with H2 In-Memory Database (Quick Start)

Edit `application.properties`:
```properties
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
```

Add H2 dependency to `pom.xml`:
```xml
<dependency>
    <groupId>com.h2database</groupId>
    <artifactId>h2</artifactId>
    <scope>runtime</scope>
</dependency>
```

### Option 2: Run with PostgreSQL

1. Start PostgreSQL:
```bash
# Windows with Chocolatey
choco install postgresql

# Or use Docker
docker run -e POSTGRES_PASSWORD=postgres -d -p 5432:5432 postgres:15
```

2. Create database:
```sql
CREATE DATABASE sentinel_ai_db;
```

### Build & Run

```bash
# Build
cd sentinalAI
mvn clean install

# Run
mvn spring-boot:run
```

Application starts on `http://localhost:8080`

## Access the Application

### 🎨 Interactive Dashboard
```
http://localhost:8080
```

### 📚 API Documentation (Swagger UI)
```
http://localhost:8080/swagger-ui.html
```

### 📊 API Endpoints

#### Services
```
POST   /api/v1/services                    # Create service
GET    /api/v1/services                    # Get all services
GET    /api/v1/services/{serviceId}        # Get service details
GET    /api/v1/services/name/{serviceName} # Get by name
PUT    /api/v1/services/{serviceId}/health # Update health status
```

#### Metrics
```
POST   /api/v1/metrics/ingest              # Ingest metric
GET    /api/v1/metrics/service/{serviceId} # Get metrics (with time range)
GET    /api/v1/metrics/anomalies           # Get anomalous metrics
```

#### Logs
```
POST   /api/v1/logs/ingest                 # Ingest log entry
GET    /api/v1/logs/service/{serviceId}    # Get logs by service
GET    /api/v1/logs/trace/{traceId}        # Get logs by trace
GET    /api/v1/logs/request/{requestId}    # Get logs by request
GET    /api/v1/logs/service/{id}/errors    # Get error/warn logs
```

#### AI & Reasoning
```
POST   /api/v1/ai/chat                     # Ask AI questions
POST   /api/v1/ai/explain-anomaly          # Generate AI explanation
GET    /api/v1/ai/incident-summary         # Generate incident summary
```

## Demo Walkthrough

### Step 1: View Dashboard
Navigate to http://localhost:8080 - Shows:
- System health status (Order API: DEGRADED)
- Key metrics showing 37% latency spike
- Detected anomalies
- AI root cause analysis
- Recommended actions

### Step 2: Try AI Chat
Ask questions like:
- "Why did latency increase?"
- "Which deployment happened before the anomaly?"
- "Which users are impacted?"
- "What should engineers investigate first?"
- "What is the root cause?"

### Step 3: View Pre-loaded Data
The application auto-generates demo data on startup:
- 4 Services (order-api, inventory-service, payment-service, checkout-api)
- Normal baseline metrics (50ms latency, <1% error rate)
- Anomalous metrics (87ms latency, retry storms)
- Realistic error logs showing retry attempts

### Step 4: Explore API
Visit Swagger UI at http://localhost:8080/swagger-ui.html to:
- Try all endpoints
- See request/response examples
- Test the anomaly detection flow

## Presentation Talking Points

### Problem Statement
> "Modern systems generate enormous volumes of logs, metrics, and telemetry. But visibility alone isn't enough. Engineering teams still spend significant time understanding why systems slowed down, why errors increased, or which dependency failed silently."

### Silent Failures
> "The biggest challenge is not complete outages. It's silent failures - gradual latency increases, intermittent errors, retry storms - that remain invisible until users complain."

### Sentinel AI Value
> "Instead of engineers jumping between dashboards, Sentinel AI acts as an intelligent reliability engineer. It continuously watches system behavior, detects subtle anomalies, correlates related events, and explains root causes automatically."

### Key Differentiator
> "Sentinel AI transforms raw observability data into actionable operational intelligence. Teams understand incidents in minutes instead of hours."

## Extending the Demo

### Add Real Metrics
Modify `DataInitializer.java` to ingest real Prometheus/Datadog metrics:
```java
// Replace demo data with actual system metrics
List<Metric> realMetrics = externalMetricsService.fetchMetrics();
metricRepository.saveAll(realMetrics);
```

### Integrate with Slack
Add Slack notifications when anomalies detected:
```java
@Component
public class SlackNotificationService {
    public void notifyIncident(Anomaly anomaly) {
        // Send to Slack channel
    }
}
```

### Connect Real LLM
Replace mock AI responses with OpenAI or Ollama:
```java
private String generateExplanationWithLLM(String prompt) {
    return openAIClient.chat(prompt);
}
```

### Add Auto-Remediation
Execute remediation workflows:
```java
if (anomaly.getSeverity().equals("CRITICAL")) {
    kafkaTemplate.send("remediation-topic", remediationAction);
}
```

## Performance & Production Considerations

- **Database**: Currently using `create-drop` for demo. Use `validate` or migrations in production
- **Kafka**: Optional for demo, required for real-time incident streaming in production
- **Elasticsearch**: Optional for demo, recommended for log search at scale
- **Caching**: Add Redis for frequently accessed metrics
- **Security**: Add Spring Security with OAuth2 for API authentication
- **Rate Limiting**: Add API rate limiting for production deployment

## Testing

```bash
# Run tests
mvn test

# Run with coverage
mvn clean test jacoco:report
```

## Docker Deployment

Create `Dockerfile`:
```dockerfile
FROM openjdk:17-slim
COPY target/sentinalAI-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-jar", "app.jar"]
```

```bash
docker build -t sentinel-ai:latest .
docker run -p 8080:8080 sentinel-ai:latest
```

## Troubleshooting

### Port 8080 Already in Use
```bash
# Change port in application.properties
server.port=8081
```

### PostgreSQL Connection Error
```bash
# Use H2 in-memory for quick demo
# Or ensure PostgreSQL is running
docker run -p 5432:5432 -e POSTGRES_PASSWORD=postgres postgres:15
```

### Swagger UI Not Loading
Check that `springdoc-openapi-starter-webmvc-ui` dependency is correctly added to pom.xml

## Key Files for Presentation

### Dashboard Demo
- `src/main/resources/static/index.html` - Live demo interface

### API Examples
All APIs documented in Swagger at http://localhost:8080/swagger-ui.html

### Demo Data
- `src/main/java/.../config/DataInitializer.java` - Pre-loaded realistic scenario

### AI Reasoning
- `src/main/java/.../services/AIReasoningService.java` - Root cause explanations

## Next Steps for POC

1. **Phase 1**: Integrate with client's observability stack (Prometheus/Datadog)
2. **Phase 2**: Train anomaly detection models on historical data
3. **Phase 3**: Connect to incident management (PagerDuty/Jira)
4. **Phase 4**: Deploy to staging environment for 2-week evaluation
5. **Phase 5**: Measure MTTR reduction and operational impact

## Contact & Support

For demo inquiries or technical questions, reach out to the Sentinel AI team.

---

**🎯 Remember**: The goal of this demo is not to show dashboards. It's to convince the client that Sentinel AI acts like an intelligent reliability engineer, reducing MTTR, improving reliability, and lowering operational effort.


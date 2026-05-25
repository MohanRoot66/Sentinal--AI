# SENTINEL AI - QUICK START DEPLOYMENT GUIDE

## 🚀 FASTEST WAY TO RUN THE DEMO (5 minutes)

### Option 1: Run as Pre-Built JAR (Recommended for Demo)

```bash
cd sentinalAI
mvn clean package -DskipTests

# Then run:
java -jar target/sentinalAI-0.0.1-SNAPSHOT.jar
```

Application available at: http://localhost:8080

### Option 2: Run Directly from Maven

```bash
cd sentinalAI
mvn spring-boot:run
```

### Option 3: Use Docker

```bash
# Build Docker image
docker build -t sentinel-ai:latest .

# Run container
docker run -p 8080:8080 -e SERVER_PORT=8080 sentinel-ai:latest
```

---

## 📋 COMPILATION ISSUES & FIXES

If you see compilation errors related to Lombok, add the following to your IDE:

### For IntelliJ IDEA
1. File → Settings → Build, Execution, Deployment → Compiler → Annotation Processors
2. Check "Enable annotation processing"
3. Rebuild project: Build → Rebuild Project

### For Eclipse
1. Install Lombok from: https://projectlombok.org/download
2. Run: `java -jar lombok.jar`
3. Select Eclipse installation directory
4. Restart Eclipse

### For VS Code
1. Add to `.vscode/settings.json`:
```json
{
    "java.configuration.updateBuildConfiguration": "automatic",
    "[java]": {
        "editor.formatOnSave": true
    }
}
```

---

## ✅ VERIFICATION CHECKLIST

After starting the application, verify:

- [ ] Application starts without errors
- [ ] Access http://localhost:8080 - Dashboard loads
- [ ] Access http://localhost:8080/swagger-ui.html - API docs available
- [ ] Access http://localhost:8080/h2-console - H2 database console works
- [ ] Check application.log for any warnings

---

## 🔧 TROUBLESHOOTING

### Issue: "Port 8080 already in use"
```bash
# Windows - Find and kill process on port 8080
netstat -ano | findstr :8080
taskkill /PID <PID> /F

# Linux/Mac
lsof -ti:8080 | xargs kill -9
```

### Issue: "Cannot find symbol" errors
This is usually a Lombok processing issue:
```bash
# Clean and rebuild
mvn clean install -U

# Or with skipTests for faster build
mvn clean install -DskipTests
```

### Issue: H2 Database connection error
The application uses in-memory H2 by default (no external dependencies).
If you see connection errors, restart the application.

### Issue: Swagger UI shows "Unable to render this definition"
Navigate directly to: http://localhost:8080/swagger-ui/index.html

---

## 📊 ACCESSING THE DEMO

### Dashboard
```
http://localhost:8080
```
Shows:
- System health status
- Key metrics (latency spike 37%)
- Detected anomalies
- AI root cause analysis
- Chat interface
- Incident summary

### API Documentation
```
http://localhost:8080/swagger-ui.html
```
- Try all endpoints
- See request/response examples
- Test anomaly detection

### Database Console (Optional)
```
http://localhost:8080/h2-console
```
JDBC URL: `jdbc:h2:mem:testdb`
User: `sa`
Password: (leave blank)

---

## 🎯 DEMO FLOW USING POSTMAN

### 1. Check Services
```bash
curl http://localhost:8080/api/v1/services
```
Response shows 4 services: order-api, inventory-service, payment-service, checkout-api

### 2. Get Anomalies
```bash
curl http://localhost:8080/api/v1/metrics/anomalies
```
Response shows latency spike metrics

### 3. Get Logs
```bash
curl http://localhost:8080/api/v1/logs/service/order-api
```
Response shows error logs during the incident

### 4. AI Chat
```bash
curl -X POST http://localhost:8080/api/v1/ai/chat \
  -H "Content-Type: application/json" \
  -d '{"question":"Why did latency increase?"}'
```

### 5. Get Incident Summary
```bash
curl "http://localhost:8080/api/v1/ai/incident-summary?serviceId=order-api&anomalyType=LATENCY_SPIKE&anomalyScore=0.87"
```

---

## 🐳 DOCKER DEPLOYMENT

### Build Dockerfile
```dockerfile
FROM openjdk:17-slim
WORKDIR /app
COPY target/sentinalAI-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

### Build & Run
```bash
docker build -t sentinel-ai:latest .
docker run -p 8080:8080 sentinel-ai:latest
```

### Docker Compose (with PostgreSQL)
```yaml
version: '3.8'
services:
  sentinel-ai:
    build: .
    ports:
      - "8080:8080"
    environment:
      SPRING_DATASOURCE_URL: jdbc:postgresql://db:5432/sentinel_ai
      SPRING_DATASOURCE_USERNAME: postgres
      SPRING_DATASOURCE_PASSWORD: postgres
    depends_on:
      - db
  
  db:
    image: postgres:15
    environment:
      POSTGRES_DB: sentinel_ai
      POSTGRES_PASSWORD: postgres
    volumes:
      - postgres_data:/var/lib/postgresql/data

volumes:
  postgres_data:
```

Run with: `docker-compose up`

---

## 🚀 PRODUCTION DEPLOYMENT OPTIONS

### Option 1: AWS EC2 + RDS
1. Deploy JAR to EC2 instance
2. Use RDS PostgreSQL for database
3. Update application.properties with RDS endpoint
4. Configure security groups for port 8080

### Option 2: Kubernetes
```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: sentinel-ai
spec:
  replicas: 3
  selector:
    matchLabels:
      app: sentinel-ai
  template:
    metadata:
      labels:
        app: sentinel-ai
    spec:
      containers:
      - name: sentinel-ai
        image: sentinel-ai:latest
        ports:
        - containerPort: 8080
        env:
        - name: SPRING_DATASOURCE_URL
          value: jdbc:postgresql://postgres-service:5432/sentinel_ai
```

### Option 3: Cloud Run (GCP)
```bash
gcloud run deploy sentinel-ai \
  --source . \
  --platform managed \
  --region us-central1 \
  --allow-unauthenticated
```

### Option 4: Azure App Service
```bash
az webapp deployment source config-zip \
  --resource-group myResourceGroup \
  --name sentinel-ai-app \
  --src target/sentinalAI-0.0.1-SNAPSHOT.jar
```

---

## 📈 PERFORMANCE TUNING

### JVM Tuning
```bash
java -Xmx1024m -Xms512m -XX:+UseG1GC \
  -jar target/sentinalAI-0.0.1-SNAPSHOT.jar
```

### Application Properties Optimization
```properties
# Connection pooling
spring.datasource.hikari.maximum-pool-size=20
spring.datasource.hikari.minimum-idle=5

# JPA batching
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# Caching
spring.cache.type=redis
spring.redis.host=localhost
spring.redis.port=6379
```

---

## 📝 DATABASE MIGRATION

### Switch from H2 to PostgreSQL

1. Update `pom.xml` - ensure PostgreSQL driver:
```xml
<dependency>
    <groupId>org.postgresql</groupId>
    <artifactId>postgresql</artifactId>
    <scope>runtime</scope>
</dependency>
```

2. Update `application.properties`:
```properties
spring.datasource.url=jdbc:postgresql://localhost:5432/sentinel_ai_db
spring.datasource.username=postgres
spring.datasource.password=postgres
spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
```

3. Create database:
```sql
CREATE DATABASE sentinel_ai_db;
```

4. Run application:
```bash
mvn clean install
mvn spring-boot:run
```

---

## 🔒 SECURITY CONFIGURATION

### Add Spring Security

1. Add dependency to `pom.xml`:
```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-security</artifactId>
</dependency>
```

2. Create SecurityConfig:
```java
@Configuration
@EnableWebSecurity
public class SecurityConfig {
    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .authorizeHttpRequests(authz -> authz
                .requestMatchers("/swagger-ui/**", "/v3/api-docs/**").permitAll()
                .requestMatchers("/api/v1/**").authenticated()
                .anyRequest().permitAll())
            .httpBasic();
        return http.build();
    }
}
```

### CORS Configuration

```java
@Configuration
public class CorsConfig {
    @Bean
    public WebMvcConfigurer corsConfigurer() {
        return new WebMvcConfigurer() {
            @Override
            public void addCorsMappings(CorsRegistry registry) {
                registry.addMapping("/api/**")
                    .allowedOrigins("*")
                    .allowedMethods("GET", "POST", "PUT", "DELETE");
            }
        };
    }
}
```

---

## 📊 MONITORING & LOGGING

### Add Micrometer/Actuator

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-actuator</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

### Update Properties
```properties
management.endpoints.web.exposure.include=health,metrics,prometheus
management.metrics.export.prometheus.enabled=true
```

### Access Metrics
```
http://localhost:8080/actuator/metrics
http://localhost:8080/actuator/prometheus
```

### Integrate with Prometheus
```yaml
# prometheus.yml
global:
  scrape_interval: 15s

scrape_configs:
  - job_name: 'sentinel-ai'
    static_configs:
      - targets: ['localhost:8080']
    metrics_path: '/actuator/prometheus'
```

---

## 🎓 NEXT STEPS

1. ✅ Run the application
2. ✅ View the dashboard
3. ✅ Test the API with Swagger
4. ✅ Try the AI chat interface
5. ✅ Review demo data in H2 console
6. ✅ Customize for your environment
7. ✅ Deploy to production

For more information, see:
- `README.md` - Architecture & components
- `DEMO_GUIDE.md` - Live demo walkthrough
- `PRESENTATION_SLIDES.md` - Slide deck

---

**Questions?** Contact the Sentinel AI team.


package com.example.sentinalAI.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.experimental.SuperBuilder;

import java.time.LocalDateTime;

@Entity
@Table(name = "services")
@Data
@NoArgsConstructor
@AllArgsConstructor
@SuperBuilder
public class Service extends BaseEntity {
    @Column(name = "service_name", nullable = false, unique = true)
    private String serviceName;

    @Column(name = "service_type")
    private String serviceType; // API, Database, Cache, Message Queue, etc.

    @Column(name = "description")
    private String description;

    @Column(name = "is_healthy")
    private Boolean isHealthy = true;

    @Column(name = "last_checked")
    private LocalDateTime lastChecked;

    @Column(name = "health_status")
    private String healthStatus; // HEALTHY, DEGRADED, CRITICAL
}


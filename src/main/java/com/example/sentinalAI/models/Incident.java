package com.example.sentinalAI.models;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "incidents")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Incident extends BaseEntity {
    @Column(name = "incident_title")
    private String incidentTitle;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Column(name = "severity")
    private String severity; // LOW, MEDIUM, HIGH, CRITICAL

    @Column(name = "status")
    private String status; // DETECTED, ACKNOWLEDGED, INVESTIGATING, RESOLVED, CLOSED

    @Column(name = "detected_at")
    private LocalDateTime detectedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "root_cause", columnDefinition = "TEXT")
    private String rootCause;

    @Column(name = "impact_summary", columnDefinition = "TEXT")
    private String impactSummary;

    @Column(name = "mttr_minutes")
    private Long mttrMinutes;
}


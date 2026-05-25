package com.example.sentinalAI.repositories;

import com.example.sentinalAI.models.Metric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface MetricRepository extends JpaRepository<Metric, String> {
    List<Metric> findByServiceIdAndMetricNameAndTimestampBetween(
            String serviceId, String metricName, LocalDateTime start, LocalDateTime end);

    List<Metric> findByIsAnomalyTrue();

    @Query("SELECT m FROM Metric m WHERE m.service.id = ?1 ORDER BY m.timestamp DESC LIMIT 100")
    List<Metric> findLatestMetricsByService(String serviceId);
}


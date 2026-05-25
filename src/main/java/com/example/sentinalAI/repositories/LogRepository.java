package com.example.sentinalAI.repositories;

import com.example.sentinalAI.models.Log;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface LogRepository extends JpaRepository<Log, String> {
    List<Log> findByServiceIdAndTimestampBetweenOrderByTimestampDesc(
            String serviceId, LocalDateTime start, LocalDateTime end);

    List<Log> findByTraceId(String traceId);

    List<Log> findByRequestId(String requestId);

    @Query("SELECT l FROM Log l WHERE l.service.id = ?1 AND l.logLevel IN ('ERROR', 'WARN') ORDER BY l.timestamp DESC")
    List<Log> findErrorAndWarnLogsByService(String serviceId);

    @Query("SELECT l FROM Log l ORDER BY l.timestamp DESC LIMIT 200")
    List<Log> findRecentLogs();

    @Query("SELECT l FROM Log l WHERE l.timestamp >= ?1 ORDER BY l.timestamp ASC")
    List<Log> findLogsSince(LocalDateTime since);

    @Query("SELECT l FROM Log l WHERE l.service.serviceName = ?1 ORDER BY l.timestamp ASC")
    List<Log> findByServiceName(String serviceName);
}

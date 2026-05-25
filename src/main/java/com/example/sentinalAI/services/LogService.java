package com.example.sentinalAI.services;

import com.example.sentinalAI.dto.LogDTO;
import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.models.Service;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.repositories.ServiceRepository;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@org.springframework.stereotype.Service
@RequiredArgsConstructor
public class LogService {
    private final LogRepository logRepository;
    private final ServiceRepository serviceRepository;

    public Log ingestLog(LogDTO logDTO) {
        Optional<Service> service = serviceRepository.findById(logDTO.getServiceId());
        if (service.isEmpty()) {
            throw new RuntimeException("Service not found: " + logDTO.getServiceId());
        }

        Log log = Log.builder()
                .service(service.get())
                .logLevel(logDTO.getLogLevel())
                .message(logDTO.getMessage())
                .timestamp(logDTO.getTimestamp() != null ? logDTO.getTimestamp() : LocalDateTime.now())
                .traceId(logDTO.getTraceId())
                .userId(logDTO.getUserId())
                .requestId(logDTO.getRequestId())
                .statusCode(logDTO.getStatusCode())
                .latencyMs(logDTO.getLatencyMs())
                .build();

        return logRepository.save(log);
    }

    public List<Log> getLogsByService(String serviceId, LocalDateTime startTime, LocalDateTime endTime) {
        return logRepository.findByServiceIdAndTimestampBetweenOrderByTimestampDesc(
                serviceId, startTime, endTime);
    }

    public List<Log> getLogsByTraceId(String traceId) {
        return logRepository.findByTraceId(traceId);
    }

    public List<Log> getLogsByRequestId(String requestId) {
        return logRepository.findByRequestId(requestId);
    }

    public List<Log> getErrorAndWarnLogs(String serviceId) {
        return logRepository.findErrorAndWarnLogsByService(serviceId);
    }
}


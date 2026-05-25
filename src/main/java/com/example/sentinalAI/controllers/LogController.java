package com.example.sentinalAI.controllers;

import com.example.sentinalAI.dto.LogDTO;
import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.services.LogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1/logs")
@RequiredArgsConstructor
@Tag(name = "Logs", description = "Log collection and retrieval endpoints")
public class LogController {
    private final LogService logService;

    @PostMapping("/ingest")
    @Operation(summary = "Ingest a log entry", description = "Receive and store a log from applications")
    public ResponseEntity<Log> ingestLog(@RequestBody LogDTO logDTO) {
        Log log = logService.ingestLog(logDTO);
        return ResponseEntity.ok(log);
    }

    @GetMapping("/service/{serviceId}")
    @Operation(summary = "Get logs by service", description = "Retrieve logs for a specific service within a time range")
    public ResponseEntity<List<Log>> getLogsByService(
            @PathVariable String serviceId,
            @RequestParam(required = false) LocalDateTime startTime,
            @RequestParam(required = false) LocalDateTime endTime) {
        LocalDateTime start = startTime != null ? startTime : LocalDateTime.now().minusHours(1);
        LocalDateTime end = endTime != null ? endTime : LocalDateTime.now();
        List<Log> logs = logService.getLogsByService(serviceId, start, end);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/trace/{traceId}")
    @Operation(summary = "Get logs by trace ID", description = "Retrieve all logs for a specific distributed trace")
    public ResponseEntity<List<Log>> getLogsByTraceId(@PathVariable String traceId) {
        List<Log> logs = logService.getLogsByTraceId(traceId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/request/{requestId}")
    @Operation(summary = "Get logs by request ID", description = "Retrieve all logs for a specific request")
    public ResponseEntity<List<Log>> getLogsByRequestId(@PathVariable String requestId) {
        List<Log> logs = logService.getLogsByRequestId(requestId);
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/service/{serviceId}/errors")
    @Operation(summary = "Get error and warning logs", description = "Retrieve error and warning level logs for a service")
    public ResponseEntity<List<Log>> getErrorLogs(@PathVariable String serviceId) {
        List<Log> logs = logService.getErrorAndWarnLogs(serviceId);
        return ResponseEntity.ok(logs);
    }
}


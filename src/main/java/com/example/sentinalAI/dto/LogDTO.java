package com.example.sentinalAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LogDTO {
    private String serviceId;
    private String logLevel;
    private String message;
    private LocalDateTime timestamp;
    private String traceId;
    private String userId;
    private String requestId;
    private Integer statusCode;
    private Long latencyMs;
}


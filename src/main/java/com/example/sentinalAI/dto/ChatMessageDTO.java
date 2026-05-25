package com.example.sentinalAI.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageDTO {
    private String question;
    private String answer;
    private String context; // Additional context about the incident/anomaly
    private Long timestamp;
}


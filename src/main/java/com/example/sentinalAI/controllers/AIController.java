package com.example.sentinalAI.controllers;

import com.example.sentinalAI.services.AIReasoningService;
import com.example.sentinalAI.dto.ChatMessageDTO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "AI-powered question answering and incident reasoning")
public class AIController {
    private final AIReasoningService aiReasoningService;

    @PostMapping("/chat")
    @Operation(summary = "Ask AI questions about incidents", description = "Natural language interface to query AI about system behavior")
    public ResponseEntity<ChatMessageDTO> askQuestion(@RequestBody Map<String, String> request) {
        String question = request.get("question");
        String answer = aiReasoningService.answerQuestion(question);

        ChatMessageDTO response = ChatMessageDTO.builder()
                .question(question)
                .answer(answer)
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.ok(response);
    }

    @PostMapping("/explain-anomaly")
    @Operation(summary = "Generate AI explanation for anomaly", description = "Generate comprehensive AI-driven root cause analysis")
    public ResponseEntity<Map<String, Object>> explainAnomaly(
            @RequestParam String serviceId,
            @RequestParam String anomalyType,
            @RequestParam Double anomalyScore,
            @RequestParam(required = false) String affectedMetric) {

        String explanation = aiReasoningService.explainAnomaly(serviceId, anomalyType,
                anomalyScore, affectedMetric != null ? affectedMetric : "latency");

        Map<String, Object> response = new HashMap<>();
        response.put("serviceId", serviceId);
        response.put("anomalyType", anomalyType);
        response.put("anomalyScore", anomalyScore);
        response.put("explanation", explanation);
        response.put("timestamp", System.currentTimeMillis());

        return ResponseEntity.ok(response);
    }

    @GetMapping("/incident-summary")
    @Operation(summary = "Generate incident summary", description = "Auto-generate incident summary for reporting")
    public ResponseEntity<Map<String, Object>> generateIncidentSummary(
            @RequestParam String serviceId,
            @RequestParam String anomalyType,
            @RequestParam Double anomalyScore) {

        Map<String, Object> summary = aiReasoningService.generateIncidentSummary(
                serviceId, anomalyType, anomalyScore);

        return ResponseEntity.ok(summary);
    }
}


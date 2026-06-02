package com.example.sentinalAI.controllers;

import com.example.sentinalAI.dto.ChatMessageDTO;
import com.example.sentinalAI.models.Log;
import com.example.sentinalAI.repositories.LogRepository;
import com.example.sentinalAI.services.AIOrchestrator;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/ai")
@RequiredArgsConstructor
@Tag(name = "AI Assistant", description = "AI-powered question answering via Google Gemini")
public class AIController {

    private final AIOrchestrator aiOrchestrator;
    private final LogRepository logRepository;

    @PostMapping("/chat")
    @Operation(summary = "Chat with AI about current logs and incidents")
    public ResponseEntity<ChatMessageDTO> askQuestion(@RequestBody Map<String, String> request) {
        String question   = request.get("question");
        String rcaContext = request.getOrDefault("rcaContext", "");
        String scenario   = request.getOrDefault("scenario", "");

        // Build context: RCA output (if available) + recent log summary
        String logContext = buildLogContext(scenario);
        String fullContext = rcaContext != null && !rcaContext.isBlank()
                ? "## Previous RCA Analysis\n" + rcaContext + "\n\n## Live Log Summary\n" + logContext
                : logContext;

        String answer = aiOrchestrator.answerQuestion(question, fullContext);

        ChatMessageDTO response = ChatMessageDTO.builder()
                .question(question)
                .answer(answer)
                .timestamp(System.currentTimeMillis())
                .build();

        return ResponseEntity.ok(response);
    }

    private String buildLogContext(String scenario) {
        try {
            List<Log> recent = scenario != null && !scenario.isBlank()
                    ? logRepository.findByScenario(scenario.toUpperCase())
                    : logRepository.findLogsSince(LocalDateTime.now().minusHours(2));
            if (recent.isEmpty()) return "No recent logs available.";

            long errors = recent.stream().filter(l -> "ERROR".equals(l.getLogLevel())).count();
            long warns  = recent.stream().filter(l -> "WARN".equals(l.getLogLevel())).count();
            long peak   = recent.stream().filter(l -> l.getLatencyMs() != null)
                    .mapToLong(Log::getLatencyMs).max().orElse(0);

            String sampleLogs = recent.stream()
                    .filter(l -> "ERROR".equals(l.getLogLevel()) || "WARN".equals(l.getLogLevel()))
                    .limit(10)
                    .map(l -> String.format("[%s][%s][%s] %s",
                            l.getTimestamp() != null ? l.getTimestamp().format(DateTimeFormatter.ofPattern("HH:mm:ss")) : "?",
                            l.getLogLevel(),
                            l.getService() != null ? l.getService().getServiceName() : "?",
                            l.getMessage()))
                    .collect(Collectors.joining("\n"));

            return String.format("Scenario: %s | Total: %d | Errors: %d | Warnings: %d | Peak latency: %dms\nKey logs:\n%s",
                    scenario, recent.size(), errors, warns, peak, sampleLogs);
        } catch (Exception e) {
            return "Could not retrieve log context.";
        }
    }
}

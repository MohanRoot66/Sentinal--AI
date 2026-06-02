package com.example.sentinalAI.services;

import com.example.sentinalAI.models.Log;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * AIOrchestrator — routes AI requests to the configured provider.
 *
 * ai.provider=ollama  → local Ollama model (default)
 * ai.provider=gemini  → Google Gemini cloud API
 * ai.provider=auto    → try Ollama first, fall back to Gemini if down
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class AIOrchestrator {

    private final OllamaService ollamaService;
    private final GeminiService geminiService;

    @Value("${ai.provider:ollama}")
    private String provider;

    public String analyzeLogsForRCA(List<Log> logs, String scenario) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> {
                log.info("[AIOrchestrator] Using Gemini (cloud)");
                yield geminiService.analyzeLogsForRCA(logs, scenario);
            }
            case "auto" -> {
                log.info("[AIOrchestrator] Auto-mode: checking Ollama...");
                if (ollamaService.isAvailable()) {
                    log.info("[AIOrchestrator] Ollama UP → using local model");
                    yield ollamaService.analyzeLogsForRCA(logs, scenario);
                } else {
                    log.warn("[AIOrchestrator] Ollama DOWN → falling back to Gemini");
                    yield geminiService.analyzeLogsForRCA(logs, scenario);
                }
            }
            default -> { // "ollama" (default)
                log.info("[AIOrchestrator] Using Ollama (local) | model: mistral");
                yield ollamaService.analyzeLogsForRCA(logs, scenario);
            }
        };
    }

    public String answerQuestion(String question, String logContext) {
        return switch (provider.toLowerCase()) {
            case "gemini" -> geminiService.answerQuestion(question, logContext);
            case "auto"   -> ollamaService.isAvailable()
                    ? ollamaService.answerQuestion(question, logContext)
                    : geminiService.answerQuestion(question, logContext);
            default       -> ollamaService.answerQuestion(question, logContext); // ollama
        };
    }

    public String getActiveProvider() {
        return switch (provider.toLowerCase()) {
            case "gemini" -> "Gemini (Cloud)";
            case "auto"   -> ollamaService.isAvailable() ? "Ollama (Local)" : "Gemini (Cloud - fallback)";
            default       -> "Ollama (Local)";
        };
    }
}

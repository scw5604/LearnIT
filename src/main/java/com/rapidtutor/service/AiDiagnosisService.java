package com.rapidtutor.service;

import com.rapidtutor.repository.TutoringRequestRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AiDiagnosisService {

    private static final Logger log = LoggerFactory.getLogger(AiDiagnosisService.class);
    private static final String FALLBACK = "AI Diagnosis temporarily unavailable. Proceeding with standard human review.";

    private final RestClient restClient;
    private final TutoringRequestRepository repository;

    @Value("${groq.api.key:mock-key}")
    private String apiKey;

    @Value("${groq.model:openai/gpt-oss-20b}")
    private String model;

    public AiDiagnosisService(TutoringRequestRepository repository) {
        this.repository = repository;
        this.restClient = RestClient.builder().build();
    }

    @Async
    public void generateDiagnosticScaffold(Long requestId, String course, String topic, String description) {
        if (apiKey == null || apiKey.isBlank() || "mock-key".equals(apiKey)) {
            saveDiagnosis(requestId, offlineNote(topic));
            return;
        }

        String prompt = String.format(
                "Act as an academic peer tutor. For course: %s, topic: %s, description: %s. " +
                        "Provide a 3-bullet response: 1) Underlying Core Concept, 2) Diagnostic Question to Ask, 3) Suggested Starting Step. Do NOT write code or provide the final answer.",
                course, topic, description
        );

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(Map.of("role", "user", "content", prompt))
            );

            Map<?, ?> response = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String result = GroqResponseParser.extractMessageContent(response);
            saveDiagnosis(requestId, result != null ? result : FALLBACK);
        } catch (Exception e) {
            log.warn("AI diagnosis failed: {}", e.getMessage());
            saveDiagnosis(requestId, FALLBACK);
        }
    }

    private String offlineNote(String topic) {
        return "Diagnostic Note (Offline Demo): Verify student environment and check baseline syntax for " + topic + ".";
    }

    private void saveDiagnosis(Long requestId, String text) {
        repository.findById(requestId).ifPresent(req -> {
            req.setAiDiagnosticNote(text);
            repository.save(req);
        });
    }
}

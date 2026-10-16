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
    private static final String SYSTEM_PROMPT = """
            You prepare a student for a peer tutoring session. Reply with exactly three labeled parts:
            Core concept: ...
            Diagnostic question: ...
            Starting step: ...
            Explain the idea, ask one question, and give a first step. Do not give the final answer, a worked solution, or code that completes the assignment.
            """;

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
            saveDiagnosis(requestId, SocraticNotes.offline(topic));
            return;
        }

        String studentRequest = SocraticNotes.userMessage(course, topic, description);

        try {
            Map<String, Object> requestBody = Map.of(
                    "model", model,
                    "messages", List.of(
                            Map.of("role", "system", "content", SYSTEM_PROMPT),
                            Map.of("role", "user", "content", studentRequest)
                    )
            );

            Map<?, ?> response = restClient.post()
                    .uri("https://api.groq.com/openai/v1/chat/completions")
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            String result = GroqResponseParser.extractMessageContent(response);
            saveDiagnosis(requestId, DiagnosticNoteChecker.accepts(result) ? result : SocraticNotes.offline(topic));
        } catch (Exception e) {
            log.warn("AI diagnosis failed: {}", e.getMessage());
            saveDiagnosis(requestId, SocraticNotes.offline(topic));
        }
    }

    private void saveDiagnosis(Long requestId, String text) {
        repository.findById(requestId).ifPresent(req -> {
            req.setAiDiagnosticNote(text);
            repository.save(req);
        });
    }
}

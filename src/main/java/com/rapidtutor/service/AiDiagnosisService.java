package com.rapidtutor.service;

import com.rapidtutor.repository.TutoringRequestRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

@Service
public class AiDiagnosisService {

    private final RestClient restClient;
    private final TutoringRequestRepository repository;

    // Uses a mock key if you haven't set a real one in application.properties
    @Value("${gemini.api.key:mock-key}")
    private String apiKey;

    public AiDiagnosisService(TutoringRequestRepository repository) {
        this.repository = repository;
        this.restClient = RestClient.builder().build();
    }

    @Async
    public void generateDiagnosticScaffold(Long requestId, String course, String topic, String description) {
        // Graceful fallback for offline demo mode
        if ("mock-key".equals(apiKey)) {
            saveDiagnosis(requestId, "Diagnostic Note (Offline Demo): Verify student environment and check baseline syntax for " + topic + ".");
            return;
        }

        // The strict Socratic prompt contract[cite: 2]
        String prompt = String.format(
                "Act as an academic peer tutor. For course: %s, topic: %s, description: %s. " +
                        "Provide a 3-bullet response: 1) Underlying Core Concept, 2) Diagnostic Question to Ask, 3) Suggested Starting Step. Do NOT write code or provide the final answer.",
                course, topic, description
        );

        try {
            String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-1.5-flash:generateContent?key=" + apiKey;
            var requestBody = Map.of("contents", List.of(Map.of("parts", List.of(Map.of("text", prompt)))));

            Map response = restClient.post()
                    .uri(url)
                    .header("Content-Type", "application/json")
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            // Note: For a production app, you would parse the specific JSON tree. toString() is a fast MVP workaround.
            String result = response.toString();
            saveDiagnosis(requestId, result);
        } catch (Exception e) {
            saveDiagnosis(requestId, "AI Diagnosis temporarily unavailable. Proceeding with standard human review.");
        }
    }

    private void saveDiagnosis(Long requestId, String text) {
        repository.findById(requestId).ifPresent(req -> {
            req.setAiDiagnosticNote(text);
            repository.save(req);
        });
    }
}

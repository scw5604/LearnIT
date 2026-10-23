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
    private static final String FIRST_PROMPT = """
            You are a rubber duck for a student stuck on coursework. They have not explained an attempt yet. Reply with exactly three labeled lines and no other text:
            Core concept: name the idea in one or two sentences, with no solution.
            Diagnostic question: one question about what they have already tried or where they got stuck.
            Starting step: one action they can finish in a few minutes.
            Do not give the final answer, a worked solution, or code that completes the assignment.
            """;

    private static final String FOLLOW_UP_PROMPT = """
            You are a rubber duck. The student just explained what they tried. Respond only to that explanation. Reply with exactly three labeled lines and no other text:
            Core concept: what their explanation shows they already understand, in one or two sentences.
            Diagnostic question: one follow-up aimed at the gap in what they wrote.
            Starting step: the single next thing to try, based on their words.
            Do not give the final answer, a worked solution, or code that completes the assignment. If their explanation already resolves the blocker, say they can stop here in Starting step, without writing the solution.
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

    /** Returns a diagnostic note. Uses the offline note when Groq is unset or the reply is unusable. */
    public String prepareNote(String course, String topic, String description) {
        return noteOrOffline(FIRST_PROMPT, SocraticNotes.userMessage(course, topic, description), SocraticNotes.offline(topic));
    }

    /** One follow-up that responds to what the student already tried. */
    public String prepareFollowUp(String course, String topic, String description, String note, String answer) {
        return noteOrOffline(
                FOLLOW_UP_PROMPT,
                SocraticNotes.followUpMessage(course, topic, description, note, answer),
                SocraticNotes.offlineFollowUp(topic, answer));
    }

    private String noteOrOffline(String systemPrompt, String userMessage, String fallback) {
        if (apiKey == null || apiKey.isBlank() || "mock-key".equals(apiKey)) {
            return fallback;
        }
        try {
            String result = askGroq(systemPrompt, userMessage);
            return DiagnosticNoteChecker.accepts(result) ? result : fallback;
        } catch (Exception e) {
            log.warn("AI diagnosis failed: {}", e.getMessage());
            return fallback;
        }
    }

    @Async
    public void generateDiagnosticScaffold(Long requestId, String course, String topic, String description) {
        saveDiagnosis(requestId, prepareNote(course, topic, description));
    }

    private String askGroq(String systemPrompt, String studentRequest) {
        Map<String, Object> requestBody = Map.of(
                "model", model,
                "messages", List.of(
                        Map.of("role", "system", "content", systemPrompt),
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

        return GroqResponseParser.extractMessageContent(response);
    }

    private void saveDiagnosis(Long requestId, String text) {
        repository.findById(requestId).ifPresent(req -> {
            req.setAiDiagnosticNote(text);
            repository.save(req);
        });
    }
}

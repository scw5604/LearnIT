package com.rapidtutor.service;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GroqResponseParserTest {

    @Test
    void extractsTrimmedMessageContent() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", "  Concept: loops  ")))
        );

        assertEquals("Concept: loops", GroqResponseParser.extractMessageContent(response));
    }

    @Test
    void joinsTextParts() {
        Map<String, Object> response = Map.of(
                "choices", List.of(Map.of("message", Map.of("content", List.of(
                        Map.of("text", "Core concept. "),
                        Map.of("text", "Ask why.")
                ))))
        );

        assertEquals("Core concept. Ask why.", GroqResponseParser.extractMessageContent(response));
    }

    @Test
    void returnsNullWhenTheBodyHasNoText() {
        assertNull(GroqResponseParser.extractMessageContent(null));
        assertNull(GroqResponseParser.extractMessageContent(Map.of()));
        assertNull(GroqResponseParser.extractMessageContent(Map.of("choices", List.of())));
    }
}

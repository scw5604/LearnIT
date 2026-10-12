package com.rapidtutor.service;

import java.util.List;
import java.util.Map;

public final class GroqResponseParser {

    private GroqResponseParser() {
    }

    /** Pulls the assistant text out of an OpenAI-style chat completion body. */
    public static String extractMessageContent(Map<?, ?> response) {
        if (response == null) {
            return null;
        }
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) {
            return null;
        }
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) {
            return null;
        }
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> messageMap)) {
            return null;
        }
        return textOf(messageMap.get("content"));
    }

    private static String textOf(Object content) {
        if (content instanceof String text) {
            String trimmed = text.trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        if (content instanceof List<?> parts) {
            StringBuilder combined = new StringBuilder();
            for (Object part : parts) {
                if (part instanceof String text) {
                    combined.append(text);
                } else if (part instanceof Map<?, ?> partMap && partMap.get("text") instanceof String text) {
                    combined.append(text);
                }
            }
            String trimmed = combined.toString().trim();
            return trimmed.isEmpty() ? null : trimmed;
        }
        return null;
    }
}

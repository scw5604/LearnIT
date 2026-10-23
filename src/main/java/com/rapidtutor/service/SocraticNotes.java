package com.rapidtutor.service;

public final class SocraticNotes {

    private static final int DESCRIPTION_LIMIT = 800;

    private SocraticNotes() {
    }

    public static String offline(String topic) {
        String safe = safeTopic(topic);
        return "Core concept: " + safe + " is the idea to clarify before the session.\n"
                + "Diagnostic question: What have you already tried with " + safe + "?\n"
                + "Starting step: Write the part of " + safe + " you can explain in one sentence.\n";
    }

    /**
     * A follow-up that quotes a short clip of what the student wrote.
     * Used when Groq is unset or the model reply cannot be shown.
     */
    public static String offlineFollowUp(String topic, String answer) {
        String safe = safeTopic(topic);
        String spoken = quote(answer);
        return "Core concept: You already started explaining " + safe + ".\n"
                + "Diagnostic question: What is still unclear after \"" + spoken + "\"?\n"
                + "Starting step: Rewrite one sentence from \"" + spoken + "\" in your own words.\n";
    }

    /** Course, topic, and a trimmed description. Identity fields are never included. */
    public static String userMessage(String course, String topic, String description) {
        return "Course: " + oneLine(course) + "\n"
                + "Topic: " + oneLine(topic) + "\n"
                + "Description: " + clip(description, DESCRIPTION_LIMIT);
    }

    /** First note plus the student's answer. Identity fields are never included. */
    public static String followUpMessage(String course, String topic, String description, String note, String answer) {
        return userMessage(course, topic, description) + "\n"
                + "First note: " + clip(note, DESCRIPTION_LIMIT) + "\n"
                + "What they tried: " + clip(answer, DESCRIPTION_LIMIT);
    }

    private static String safeTopic(String topic) {
        String cleaned = oneLine(topic);
        if (cleaned.isBlank() || cleaned.length() > 80 || containsAnswer(cleaned)) {
            return "this topic";
        }
        return cleaned;
    }

    private static String quote(String answer) {
        String cleaned = clip(answer, 80);
        String lower = cleaned.toLowerCase();
        if (cleaned.isBlank() || containsAnswer(cleaned)
                || lower.contains("core concept:")
                || lower.contains("diagnostic question:")
                || lower.contains("starting step:")) {
            return "what you wrote";
        }
        return cleaned;
    }

    private static boolean containsAnswer(String value) {
        String lower = value.toLowerCase();
        return lower.contains("the answer is") || lower.contains("final answer") || value.contains("```");
    }

    private static String oneLine(String value) {
        if (value == null) {
            return "";
        }
        return value.trim().replace('\r', ' ').replace('\n', ' ');
    }

    private static String clip(String value, int max) {
        String cleaned = oneLine(value);
        if (cleaned.length() <= max) {
            return cleaned;
        }
        return cleaned.substring(0, max);
    }
}

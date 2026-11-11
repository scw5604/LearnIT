package com.rapidtutor.service;

import java.util.List;

public final class SocraticNotes {

    private static final int DESCRIPTION_LIMIT = 800;

    private static final String OFFLINE_CONCEPT_END = " is the idea to clarify before the session.";
    private static final String OFFLINE_QUESTION_START = "Diagnostic question: What have you already tried with ";
    private static final String FOLLOW_UP_CONCEPT_START = "Core concept: You already started explaining ";
    private static final String FOLLOW_UP_QUESTION_START = "Diagnostic question: What is still unclear after \"";

    private SocraticNotes() {
    }

    public static String offline(String topic) {
        String safe = safeTopic(topic);
        return "Core concept: " + safe + OFFLINE_CONCEPT_END + "\n"
                + OFFLINE_QUESTION_START + safe + "?\n"
                + "Starting step: Write the part of " + safe + " you can explain in one sentence.\n";
    }

    /** True when the note is one of the offline notes built here rather than a Groq reply. */
    public static boolean isOffline(String note) {
        if (note == null) {
            return false;
        }
        List<String> lines = note.strip().lines().toList();
        if (lines.size() < 2) {
            return false;
        }
        String concept = lines.get(0);
        String question = lines.get(1);
        boolean firstNote = concept.startsWith("Core concept: ") && concept.endsWith(OFFLINE_CONCEPT_END)
                && question.startsWith(OFFLINE_QUESTION_START);
        boolean followUp = concept.startsWith(FOLLOW_UP_CONCEPT_START)
                && question.startsWith(FOLLOW_UP_QUESTION_START);
        return firstNote || followUp;
    }

    /**
     * A follow-up that quotes a short clip of what the student wrote.
     * Used when Groq is unset or the model reply cannot be shown.
     */
    public static String offlineFollowUp(String topic, String answer) {
        String safe = safeTopic(topic);
        String spoken = quote(answer);
        return FOLLOW_UP_CONCEPT_START + safe + ".\n"
                + FOLLOW_UP_QUESTION_START + spoken + "\"?\n"
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

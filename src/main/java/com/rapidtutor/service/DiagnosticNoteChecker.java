package com.rapidtutor.service;

import java.util.Locale;

public final class DiagnosticNoteChecker {

    static final String[] LABELS = {
            "Core concept:",
            "Diagnostic question:",
            "Starting step:"
    };

    static final int MAX_SECTION_LENGTH = 500;

    private DiagnosticNoteChecker() {
    }

    /** Accepts a note only when each triage section is present and has text. */
    public static boolean accepts(String note) {
        if (note == null || note.isBlank()) {
            return false;
        }
        String lower = note.toLowerCase(Locale.ROOT);
        int[] starts = new int[LABELS.length];
        int searchFrom = 0;
        for (int i = 0; i < LABELS.length; i++) {
            String label = LABELS[i].toLowerCase(Locale.ROOT);
            int found = lower.indexOf(label, searchFrom);
            if (found < 0) {
                return false;
            }
            starts[i] = found;
            searchFrom = found + label.length();
        }
        if (lower.contains("the answer is") || lower.contains("final answer") || note.contains("```")) {
            return false;
        }
        for (int i = 0; i < LABELS.length; i++) {
            int contentStart = starts[i] + LABELS[i].length();
            int contentEnd = (i + 1 < LABELS.length) ? starts[i + 1] : note.length();
            String section = note.substring(contentStart, contentEnd);
            if (section.isBlank() || section.length() > MAX_SECTION_LENGTH) {
                return false;
            }
        }
        return true;
    }
}

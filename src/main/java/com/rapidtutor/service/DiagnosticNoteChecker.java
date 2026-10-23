package com.rapidtutor.service;

import java.util.ArrayList;
import java.util.List;
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
        return !sections(note).isEmpty();
    }

    /**
     * Splits a note into its three labeled parts. Text may sit on the label line or on the following lines.
     * Returns an empty list when the note is missing a section or states an answer.
     */
    public static List<Section> sections(String note) {
        if (note == null || note.isBlank()) {
            return List.of();
        }
        String lower = note.toLowerCase(Locale.ROOT);
        if (lower.contains("the answer is") || lower.contains("final answer") || note.contains("```")) {
            return List.of();
        }
        int[] starts = new int[LABELS.length];
        int searchFrom = 0;
        for (int i = 0; i < LABELS.length; i++) {
            String label = LABELS[i].toLowerCase(Locale.ROOT);
            int found = lower.indexOf(label, searchFrom);
            if (found < 0) {
                return List.of();
            }
            starts[i] = found;
            searchFrom = found + label.length();
        }
        List<Section> sections = new ArrayList<>();
        for (int i = 0; i < LABELS.length; i++) {
            int contentStart = starts[i] + LABELS[i].length();
            int contentEnd = (i + 1 < LABELS.length) ? starts[i + 1] : note.length();
            String section = note.substring(contentStart, contentEnd);
            if (section.isBlank() || section.length() > MAX_SECTION_LENGTH) {
                return List.of();
            }
            String label = LABELS[i].substring(0, LABELS[i].length() - 1);
            sections.add(new Section(label, section.trim()));
        }
        return List.copyOf(sections);
    }

    /** One labeled part of a diagnostic note. */
    public static final class Section {
        private final String label;
        private final String body;

        public Section(String label, String body) {
            this.label = label;
            this.body = body;
        }

        public String getLabel() {
            return label;
        }

        public String getBody() {
            return body;
        }
    }
}

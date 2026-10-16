package com.rapidtutor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosticNoteCheckerTest {

    @Test
    void acceptsANoteWithAllThreeSections() {
        String note = """
                Core concept: Addition combines two quantities.
                Diagnostic question: What does the plus sign tell you to do with the two numbers?
                Starting step: Rewrite the expression in words before you compute it.
                """;

        assertTrue(DiagnosticNoteChecker.accepts(note));
    }

    @Test
    void rejectsAOneLineAnswer() {
        assertFalse(DiagnosticNoteChecker.accepts("8"));
    }

    @Test
    void rejectsANoteMissingALabel() {
        String note = """
                Core concept: Addition combines two quantities.
                Starting step: Rewrite the expression in words before you compute it.
                """;

        assertFalse(DiagnosticNoteChecker.accepts(note));
    }

    @Test
    void rejectsANoteThatStatesTheAnswer() {
        String note = """
                Core concept: The answer is 8.
                Diagnostic question: What does the plus sign tell you to do?
                Starting step: Rewrite the expression in words.
                """;

        assertFalse(DiagnosticNoteChecker.accepts(note));
    }

    @Test
    void rejectsASectionLongerThanFiveHundredCharacters() {
        String note = "Core concept: " + "a".repeat(501) + "\n"
                + "Diagnostic question: What does the plus sign tell you to do?\n"
                + "Starting step: Rewrite the expression in words.\n";

        assertFalse(DiagnosticNoteChecker.accepts(note));
    }

    @Test
    void offlineNotePassesTheChecker() {
        assertTrue(DiagnosticNoteChecker.accepts(SocraticNotes.offline("loops")));
        String message = SocraticNotes.userMessage("IST 440W", "Loops", "a".repeat(900));
        assertTrue(message.startsWith("Course: IST 440W\nTopic: Loops\nDescription: "));
        assertFalse(message.contains("a".repeat(801)));
    }
}

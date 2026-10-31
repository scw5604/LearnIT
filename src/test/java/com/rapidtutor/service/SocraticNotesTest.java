package com.rapidtutor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class SocraticNotesTest {

    @Test
    void anAnswerInTheTopicIsReplaced() {
        String note = SocraticNotes.offline("the answer is 4");

        assertEquals(SocraticNotes.offline("this topic"), note);
        assertFalse(note.toLowerCase().contains("the answer is"));
    }
}

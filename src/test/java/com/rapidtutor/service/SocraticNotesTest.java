package com.rapidtutor.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SocraticNotesTest {

    @Test
    void anAnswerInTheTopicIsReplaced() {
        String note = SocraticNotes.offline("the answer is 4");

        assertEquals(SocraticNotes.offline("this topic"), note);
        assertFalse(note.toLowerCase().contains("the answer is"));
    }

    @Test
    void bothOfflineNotesAreRecognized() {
        assertTrue(SocraticNotes.isOffline(SocraticNotes.offline("Observer pattern")));
        assertTrue(SocraticNotes.isOffline(SocraticNotes.offline("Observer pattern").trim()));
        assertTrue(SocraticNotes.isOffline(SocraticNotes.offlineFollowUp("Observer pattern", "I added a listener.")));
    }

    @Test
    void aModelReplyIsNotOffline() {
        String live = """
                Core concept: The subject keeps a list of observers and calls each one when its data changes.
                Diagnostic question: Where does your dataset call the charts after an update?
                Starting step: Add a print statement inside notifyObservers and change one value.
                """;

        assertFalse(SocraticNotes.isOffline(live));
        assertFalse(SocraticNotes.isOffline(null));
        assertFalse(SocraticNotes.isOffline(""));
    }
}

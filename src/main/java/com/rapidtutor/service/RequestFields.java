package com.rapidtutor.service;

import com.rapidtutor.model.Courses;
import com.rapidtutor.model.TutoringRequest;

import java.util.List;
import java.util.Set;

public final class RequestFields {

    public static final List<String> MEETING_METHODS = List.of("Zoom", "Teams", "Phone Call", "In-Person");
    public static final List<String> RESPONSE_TIMES = List.of("ASAP", "Within 2 hours", "Today", "Not urgent");
    public static final List<String> EFFORTS = List.of(
            "Quick explanation (10-15 mins)",
            "Concept explanation (20-30 mins)",
            "Extended help (30-60 mins)");

    private static final Set<String> MEETING_SET = Set.copyOf(MEETING_METHODS);
    private static final Set<String> RESPONSE_SET = Set.copyOf(RESPONSE_TIMES);
    private static final Set<String> EFFORT_SET = Set.copyOf(EFFORTS);

    private RequestFields() {
    }

    /** Copies only the fields a student is allowed to set. */
    public static TutoringRequest copy(String course, String topic, String description, String meetingMethod,
                                       String responseTime, String effort, Integer bounty) {
        String canonical = Courses.canonical(course);
        if (canonical == null) {
            throw new IllegalArgumentException("Choose one of the listed courses.");
        }
        String cleanTopic = required(topic, "Enter a topic.", 120);
        String cleanDescription = required(description, "Describe the concept you are stuck on.", 2000);
        if (!MEETING_SET.contains(meetingMethod)) {
            throw new IllegalArgumentException("Choose a meeting method.");
        }
        if (!RESPONSE_SET.contains(responseTime)) {
            throw new IllegalArgumentException("Choose a response time.");
        }
        if (!EFFORT_SET.contains(effort)) {
            throw new IllegalArgumentException("Choose a session length.");
        }

        TutoringRequest request = new TutoringRequest();
        request.setCourse(canonical);
        request.setTopic(cleanTopic);
        request.setShortDescription(cleanDescription);
        request.setPreferredMeetingMethod(meetingMethod);
        request.setDesiredResponseTime(responseTime);
        request.setExpectedEffort(effort);
        request.setBountyValue(bounty == null ? 0 : bounty);
        return request;
    }

    private static String required(String value, String message, int max) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException("Keep that entry under " + max + " characters.");
        }
        return trimmed;
    }
}

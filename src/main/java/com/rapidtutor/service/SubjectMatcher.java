package com.rapidtutor.service;

public final class SubjectMatcher {

    private SubjectMatcher() {
    }

    public static boolean covers(String subjects, String course) {
        if (subjects == null || course == null || course.isBlank()) {
            return false;
        }
        String wanted = course.trim();
        for (String subject : subjects.split(",")) {
            if (subject.trim().equalsIgnoreCase(wanted)) {
                return true;
            }
        }
        return false;
    }
}

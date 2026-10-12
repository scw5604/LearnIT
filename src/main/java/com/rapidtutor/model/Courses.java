package com.rapidtutor.model;

import java.util.List;

public final class Courses {

    public static final String HCDD_412 = "HCDD 412";
    public static final String IST_440W = "IST 440W";
    public static final String IST_311 = "IST 311";

    public static final List<String> OFFERED = List.of(HCDD_412, IST_440W, IST_311);

    private Courses() {
    }

    /** Returns the canonical course name, or null when the course is not offered. */
    public static String canonical(String course) {
        if (course == null) {
            return null;
        }
        String trimmed = course.trim();
        for (String offered : OFFERED) {
            if (offered.equalsIgnoreCase(trimmed)) {
                return offered;
            }
        }
        return null;
    }
}

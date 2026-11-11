package com.rapidtutor.service;

/** Trimming and length checks shared by the account and request forms. */
public final class FormText {

    private FormText() {
    }

    public static String required(String value, String message, int max) {
        return required(value, message, max, "Keep that entry under " + max + " characters.");
    }

    public static String required(String value, String message, int max, String tooLongMessage) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        String trimmed = value.trim();
        if (trimmed.length() > max) {
            throw new IllegalArgumentException(tooLongMessage);
        }
        return trimmed;
    }

    public static String blankToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}

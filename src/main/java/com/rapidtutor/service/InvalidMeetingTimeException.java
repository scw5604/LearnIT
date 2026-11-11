package com.rapidtutor.service;

/** The offered time itself is wrong, so the page marks the time field rather than the whole form. */
public class InvalidMeetingTimeException extends IllegalArgumentException {

    public InvalidMeetingTimeException(String message) {
        super(message);
    }
}

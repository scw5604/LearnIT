package com.rapidtutor.model;

/** Values stored in {@code TutoringRequest.status}. Templates compare against the same strings. */
public final class RequestStatus {

    public static final String OPEN = "OPEN";
    public static final String CLAIMED = "CLAIMED";
    public static final String RESOLVED = "RESOLVED";
    public static final String CANCELED = "CANCELED";
    public static final String EXPIRED = "EXPIRED";

    private RequestStatus() {
    }
}

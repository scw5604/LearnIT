package com.rapidtutor.model;

import java.io.Serializable;

/** The problem and diagnostic note held until the student posts or closes it. */
public class DuckDraft implements Serializable {

    public static final String SESSION_KEY = "duckDraft";

    private String course;
    private String topic;
    private String shortDescription;
    private String preferredMeetingMethod;
    private String desiredResponseTime;
    private String expectedEffort;
    private Integer bountyValue;
    private String note;
    private String reply;

    public static DuckDraft from(TutoringRequest request, String note) {
        DuckDraft draft = new DuckDraft();
        draft.course = request.getCourse();
        draft.topic = request.getTopic();
        draft.shortDescription = request.getShortDescription();
        draft.preferredMeetingMethod = request.getPreferredMeetingMethod();
        draft.desiredResponseTime = request.getDesiredResponseTime();
        draft.expectedEffort = request.getExpectedEffort();
        draft.bountyValue = request.getBountyValue();
        draft.note = note;
        return draft;
    }

    public TutoringRequest toRequest() {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription(shortDescription);
        request.setPreferredMeetingMethod(preferredMeetingMethod);
        request.setDesiredResponseTime(desiredResponseTime);
        request.setExpectedEffort(expectedEffort);
        request.setBountyValue(bountyValue == null ? 0 : bountyValue);
        return request;
    }

    public String getCourse() {
        return course;
    }

    public String getTopic() {
        return topic;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public String getPreferredMeetingMethod() {
        return preferredMeetingMethod;
    }

    public String getDesiredResponseTime() {
        return desiredResponseTime;
    }

    public String getExpectedEffort() {
        return expectedEffort;
    }

    public Integer getBountyValue() {
        return bountyValue;
    }

    public String getNote() {
        return note;
    }

    public String getReply() {
        return reply;
    }

    public void setReply(String reply) {
        this.reply = reply;
    }

    /** The follow-up when the student walked it through, otherwise the first note. */
    public String savedNote() {
        if (reply != null && !reply.isBlank()) {
            return reply;
        }
        return note;
    }
}

package com.rapidtutor.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** A student's private record of a blocker, the note, what they tried, and the outcome. */
@Entity
@Table(indexes = @Index(columnList = "student_id"))
public class DuckDraft {

    public static final String SESSION_KEY = "duckDraft";
    public static final String BOOKING_KEY = "duckBooking";

    public static final String TRYING = "TRYING";
    public static final String SOLVED = "SOLVED";
    public static final String ESCALATED = "ESCALATED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;
    private String course;
    private String topic;

    @Column(length = 2000)
    private String description;

    @Column(length = 2000)
    private String note;

    @Column(length = 2000)
    private String reply;

    @Column(length = 2000)
    private String answer;

    @Column(length = 500)
    private String takeaway;

    private String outcome;
    private Long requestId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public static DuckDraft from(TutoringRequest request, String note, Long studentId) {
        DuckDraft draft = new DuckDraft();
        draft.studentId = studentId;
        draft.course = request.getCourse();
        draft.topic = request.getTopic();
        draft.description = request.getShortDescription();
        draft.note = note;
        draft.outcome = TRYING;
        return draft;
    }

    public TutoringRequest toRequest() {
        TutoringRequest request = new TutoringRequest();
        request.setCourse(course);
        request.setTopic(topic);
        request.setShortDescription(description);
        return request;
    }

    /** The follow-up when the student walked it through, otherwise the first note. */
    public String savedNote() {
        if (reply != null && !reply.isBlank()) {
            return reply;
        }
        return note;
    }

    @PrePersist
    void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = createdAt;
        if (outcome == null) {
            outcome = TRYING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public String getCourse() {
        return course;
    }

    public String getTopic() {
        return topic;
    }

    public String getDescription() {
        return description;
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

    public String getAnswer() {
        return answer;
    }

    public void setAnswer(String answer) {
        this.answer = answer;
    }

    public String getTakeaway() {
        return takeaway;
    }

    public void setTakeaway(String takeaway) {
        this.takeaway = takeaway;
    }

    public String getOutcome() {
        return outcome;
    }

    public void setOutcome(String outcome) {
        this.outcome = outcome;
    }

    public Long getRequestId() {
        return requestId;
    }

    public void setRequestId(Long requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}

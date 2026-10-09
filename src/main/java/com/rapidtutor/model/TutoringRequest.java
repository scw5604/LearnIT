package com.rapidtutor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class TutoringRequest {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Long studentId;
    private String course;
    private String topic;
    private String shortDescription;
    private String desiredResponseTime;
    private String expectedEffort;
    private String preferredMeetingMethod;
    private String status;
    private String tutorUsername;


    private LocalDateTime creationTimestamp;
    private LocalDateTime claimedTimestamp;
    private Long assignedTutorId;
    private LocalDateTime resolvedTimestamp;

    @Version
    private Long version;

    @PrePersist
    protected void onCreate() {
        this.creationTimestamp = LocalDateTime.now();
        this.status = "OPEN";
    }

    private Integer bountyValue = 0;

    @Column(length = 3000)
    private String aiDiagnosticNote;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getStudentId() {
        return studentId;
    }

    public void setStudentId(Long studentId) {
        this.studentId = studentId;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getShortDescription() {
        return shortDescription;
    }

    public void setShortDescription(String shortDescription) {
        this.shortDescription = shortDescription;
    }

    public String getDesiredResponseTime() {
        return desiredResponseTime;
    }

    public void setDesiredResponseTime(String desiredResponseTime) {
        this.desiredResponseTime = desiredResponseTime;
    }

    public String getExpectedEffort() {
        return expectedEffort;
    }

    public void setExpectedEffort(String expectedEffort) {
        this.expectedEffort = expectedEffort;
    }

    public String getPreferredMeetingMethod() {
        return preferredMeetingMethod;
    }

    public void setPreferredMeetingMethod(String preferredMeetingMethod) {
        this.preferredMeetingMethod = preferredMeetingMethod;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getCreationTimestamp() {
        return creationTimestamp;
    }

    public void setCreationTimestamp(LocalDateTime creationTimestamp) {
        this.creationTimestamp = creationTimestamp;
    }

    public LocalDateTime getClaimedTimestamp() {
        return claimedTimestamp;
    }

    public void setClaimedTimestamp(LocalDateTime claimedTimestamp) {
        this.claimedTimestamp = claimedTimestamp;
    }

    public Long getAssignedTutorId() {
        return assignedTutorId;
    }

    public void setAssignedTutorId(Long assignedTutorId) {
        this.assignedTutorId = assignedTutorId;
    }

    public LocalDateTime getResolvedTimestamp() {
        return resolvedTimestamp;
    }

    public void setResolvedTimestamp(LocalDateTime resolvedTimestamp) {
        this.resolvedTimestamp = resolvedTimestamp;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public Integer getBountyValue() {
        return bountyValue;
    }

    public void setBountyValue(Integer bountyValue) {
        this.bountyValue = bountyValue;
    }

    public String getAiDiagnosticNote() {
        return aiDiagnosticNote;
    }

    public void setAiDiagnosticNote(String aiDiagnosticNote) {
        this.aiDiagnosticNote = aiDiagnosticNote;
    }

    public String getTutorUsername() {
        return tutorUsername;
    }

    public void setTutorUsername(String tutorUsername) {
        this.tutorUsername = tutorUsername;
    }
}
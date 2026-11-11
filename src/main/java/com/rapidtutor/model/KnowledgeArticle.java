package com.rapidtutor.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "knowledge_articles")
public class KnowledgeArticle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String course;

    @Column(nullable = false)
    private String topic;

    @Column(length = 2000, nullable = false)
    private String problemDescription;

    @Column(name = "verified_solution", length = 2000, nullable = false)
    private String noteText;

    /** Null for seeded course notes. "triage" for a note saved from a student. */
    @Column(length = 20)
    private String kind;

    private LocalDateTime createdAt = LocalDateTime.now();

    public KnowledgeArticle() {}

    public KnowledgeArticle(String course, String topic, String problemDescription, String noteText) {
        this.course = course;
        this.topic = topic;
        this.problemDescription = problemDescription;
        this.noteText = noteText;
    }

    public String getProblemDescription() {
        return problemDescription;
    }

    public void setProblemDescription(String problemDescription) {
        this.problemDescription = problemDescription;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getCourse() {
        return course;
    }

    public void setCourse(String course) {
        this.course = course;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getNoteText() {
        return noteText;
    }

    public void setNoteText(String noteText) {
        this.noteText = noteText;
    }

    public String getKind() {
        return kind;
    }

    public void setKind(String kind) {
        this.kind = kind;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
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

    @Column(length = 2000, nullable = false)
    private String verifiedSolution;

    private LocalDateTime createdAt = LocalDateTime.now();

    public KnowledgeArticle() {}

    public KnowledgeArticle(String course, String topic, String problemDescription, String verifiedSolution) {
        this.course = course;
        this.topic = topic;
        this.problemDescription = problemDescription;
        this.verifiedSolution = verifiedSolution;
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

    public String getVerifiedSolution() {
        return verifiedSolution;
    }

    public void setVerifiedSolution(String verifiedSolution) {
        this.verifiedSolution = verifiedSolution;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
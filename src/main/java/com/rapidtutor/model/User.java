package com.rapidtutor.model;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;

@Entity
@Table(name = "users")
public class User {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String username;

    private String displayName;

    @Column(unique = true)
    private String email;

    @Column(nullable = false)
    private String password;

    private String recoveryCodeHash;

    @Column(nullable = false)
    private String role;

    private String subjects;

    private String appliedSubjects;

    private Integer tokenBalance = 100;

    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean mustChangePassword;

    @Version
    private Long version;

    public String getPublicName() {
        if (displayName != null && !displayName.isBlank()) {
            return displayName;
        }
        return username;
    }

    public String getSubjects() {
        return subjects;
    }

    public void setSubjects(String subjects) {
        this.subjects = subjects;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public Integer getTokenBalance() {
        return tokenBalance;
    }

    public void setTokenBalance(Integer tokenBalance) {
        this.tokenBalance = tokenBalance;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getRecoveryCodeHash() {
        return recoveryCodeHash;
    }

    public void setRecoveryCodeHash(String recoveryCodeHash) {
        this.recoveryCodeHash = recoveryCodeHash;
    }

    public String getAppliedSubjects() {
        return appliedSubjects;
    }

    public void setAppliedSubjects(String appliedSubjects) {
        this.appliedSubjects = appliedSubjects;
    }

    public boolean isMustChangePassword() {
        return mustChangePassword;
    }

    public void setMustChangePassword(boolean mustChangePassword) {
        this.mustChangePassword = mustChangePassword;
    }
}

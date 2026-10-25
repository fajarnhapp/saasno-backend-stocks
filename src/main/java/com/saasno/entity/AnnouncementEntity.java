package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// A notice shown as a banner to everyone who is signed in, between starts_at and ends_at (either may be empty) while it is active.
@Entity
@Table(schema = "public", name = "announcement")
public class AnnouncementEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(length = 120, nullable = false)
    private String title;

    @Column(length = 1000, nullable = false)
    private String message;

    @Column(length = 16, nullable = false)
    private String level = "INFO";

    @Column(name = "starts_at")
    private LocalDateTime startsAt;

    @Column(name = "ends_at")
    private LocalDateTime endsAt;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "created_by", length = 256, updatable = false)
    private String createdBy;

    @Column(nullable = false)
    private LocalDateTime modificated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.registered = LocalDateTime.now();
        this.modificated = LocalDateTime.now();
    }

    @PreUpdate
    public void beforeUpdate() {
        this.modificated = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public LocalDateTime getStartsAt() {
        return startsAt;
    }

    public void setStartsAt(LocalDateTime startsAt) {
        this.startsAt = startsAt;
    }

    public LocalDateTime getEndsAt() {
        return endsAt;
    }

    public void setEndsAt(LocalDateTime endsAt) {
        this.endsAt = endsAt;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
    }

    public LocalDateTime getModificated() {
        return modificated;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

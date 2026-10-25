package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// A user asking for their account to be deleted. An admin approves it (the account goes to the trash) or rejects it.
@Entity
@Table(schema = "public", name = "deletion_request")
public class DeletionRequestEntity {

    public static final String PENDING = "PENDING";
    public static final String DONE = "DONE";
    public static final String REJECTED = "REJECTED";

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;

    @Column(length = 256, nullable = false, updatable = false)
    private String username;

    @Column(length = 16, nullable = false)
    private String status = PENDING;

    @Column(name = "requested_at", nullable = false, updatable = false)
    private LocalDateTime requestedAt;

    @Column(name = "handled_at")
    private LocalDateTime handledAt;

    @Column(name = "handled_by", length = 256)
    private String handledBy;

    public DeletionRequestEntity() {

    }

    public DeletionRequestEntity(String userId, String username) {
        this.userId = userId;
        this.username = username;
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.requestedAt = LocalDateTime.now();
    }

    public void handle(String newStatus, String by) {
        this.status = newStatus;
        this.handledBy = by;
        this.handledAt = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getUsername() {
        return username;
    }

    public String getStatus() {
        return status;
    }

    public LocalDateTime getRequestedAt() {
        return requestedAt;
    }

    public LocalDateTime getHandledAt() {
        return handledAt;
    }

    public String getHandledBy() {
        return handledBy;
    }
}

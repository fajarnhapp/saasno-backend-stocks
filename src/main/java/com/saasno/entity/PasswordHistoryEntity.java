package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// A password a user chose before, kept as the same Argon2 hash as the current one, so it cannot be chosen again too soon.
@Entity
@Table(schema = "public", name = "password_history")
public class PasswordHistoryEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;

    @Column(name = "password_hash", length = 256, nullable = false, updatable = false)
    private String passwordHash;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public PasswordHistoryEntity() {

    }

    public PasswordHistoryEntity(String userId, String passwordHash) {
        this.userId = userId;
        this.passwordHash = passwordHash;
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.registered = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getPasswordHash() {
        return passwordHash;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

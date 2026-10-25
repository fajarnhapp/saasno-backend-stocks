package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// A long-lived credential for another system. Only the SHA-256 of the token is stored; the token itself is shown once, when it is created.
@Entity
@Table(schema = "public", name = "api_token")
public class ApiTokenEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;

    @Column(length = 100, nullable = false)
    private String name;

    @Column(name = "token_hash", length = 64, nullable = false, updatable = false)
    private String tokenHash;

    @Column(length = 16, nullable = false, updatable = false)
    private String prefix;

    @Column(name = "expires_at", nullable = false, updatable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public ApiTokenEntity() {

    }

    public ApiTokenEntity(String userId, String name, String tokenHash, String prefix, LocalDateTime expiresAt) {
        this.userId = userId;
        this.name = name;
        this.tokenHash = tokenHash;
        this.prefix = prefix;
        this.expiresAt = expiresAt;
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.registered = LocalDateTime.now();
    }

    public boolean isUsable(LocalDateTime now) {
        return revokedAt == null && expiresAt.isAfter(now);
    }

    public String getId() {
        return id;
    }

    public String getUserId() {
        return userId;
    }

    public String getName() {
        return name;
    }

    public String getPrefix() {
        return prefix;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getLastUsedAt() {
        return lastUsedAt;
    }

    public void setLastUsedAt(LocalDateTime lastUsedAt) {
        this.lastUsedAt = lastUsedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }

    public void setRevokedAt(LocalDateTime revokedAt) {
        this.revokedAt = revokedAt;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

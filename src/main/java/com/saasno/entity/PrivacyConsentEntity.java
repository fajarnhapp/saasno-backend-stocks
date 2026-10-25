package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// A user saying they have read one version of the privacy policy.
@Entity
@Table(schema = "public", name = "privacy_consent")
public class PrivacyConsentEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 64, nullable = false, updatable = false)
    private String userId;

    @Column(nullable = false, updatable = false)
    private int version;

    @Column(name = "accepted_at", nullable = false, updatable = false)
    private LocalDateTime acceptedAt;

    public PrivacyConsentEntity() {

    }

    public PrivacyConsentEntity(String userId, int version) {
        this.userId = userId;
        this.version = version;
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.acceptedAt = LocalDateTime.now();
    }

    public String getUserId() {
        return userId;
    }

    public int getVersion() {
        return version;
    }

    public LocalDateTime getAcceptedAt() {
        return acceptedAt;
    }
}

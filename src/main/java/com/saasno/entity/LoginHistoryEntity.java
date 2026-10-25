package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// One sign-in attempt, successful or not. The username is what was typed, so attempts on accounts that do not exist are visible too.
@Entity
@Table(schema = "public", name = "login_history")
public class LoginHistoryEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "user_id", length = 64)
    private String userId;

    @Column(length = 256, nullable = false)
    private String username;

    @Column(nullable = false)
    private boolean success;

    @Column(length = 32, nullable = false)
    private String reason;

    @Column(length = 64)
    private String ip;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public LoginHistoryEntity() {

    }

    public LoginHistoryEntity(String userId, String username, boolean success, String reason, String ip, String userAgent) {
        this.userId = userId;
        this.username = username == null ? "" : username.substring(0, Math.min(username.length(), 256));
        this.success = success;
        this.reason = reason;
        this.ip = ip;
        this.userAgent = userAgent == null ? null : userAgent.substring(0, Math.min(userAgent.length(), 255));
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

    public String getUsername() {
        return username;
    }

    public boolean isSuccess() {
        return success;
    }

    public String getReason() {
        return reason;
    }

    public String getIp() {
        return ip;
    }

    public String getUserAgent() {
        return userAgent;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

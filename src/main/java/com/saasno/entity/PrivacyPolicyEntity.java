package com.saasno.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

// One published version of the privacy policy. Versions are never changed, only followed by a newer one.
@Entity
@Table(schema = "public", name = "privacy_policy")
public class PrivacyPolicyEntity {

    @Id
    private Integer version;

    // The English text, and the Indonesian text; either may be empty, not both.
    @Column(columnDefinition = "TEXT")
    private String content;

    @Column(name = "content_id", columnDefinition = "TEXT")
    private String contentId;

    @Column(name = "published_at", nullable = false)
    private LocalDateTime publishedAt;

    @Column(name = "published_by", length = 256)
    private String publishedBy;

    public PrivacyPolicyEntity() {

    }

    public PrivacyPolicyEntity(int version, String content, String contentId, String publishedBy) {
        this.version = version;
        this.content = content;
        this.contentId = contentId;
        this.publishedBy = publishedBy;
        this.publishedAt = LocalDateTime.now();
    }

    public Integer getVersion() {
        return version;
    }

    public String getContent() {
        return content;
    }

    public String getContentId() {
        return contentId;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public String getPublishedBy() {
        return publishedBy;
    }
}

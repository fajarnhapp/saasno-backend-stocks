package com.saasno.entity;

import java.time.LocalDateTime;

import com.saasno.utility.generator.ID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

// One change to a record: a creation or deletion (no field), or one field of an update with its old and new value.
@Entity
@Table(schema = "public", name = "change_log")
public class ChangeLogEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "entity_type", length = 32, nullable = false)
    private String entityType;

    @Column(name = "entity_id", length = 255, nullable = false)
    private String entityId;

    @Column(name = "entity_label", length = 256)
    private String entityLabel;

    @Column(length = 16, nullable = false)
    private String action;

    @Column(length = 64)
    private String field;

    @Column(name = "old_value", columnDefinition = "TEXT")
    private String oldValue;

    @Column(name = "new_value", columnDefinition = "TEXT")
    private String newValue;

    @Column(name = "changed_by", length = 256)
    private String changedBy;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public ChangeLogEntity() {

    }

    public ChangeLogEntity(String entityType, String entityId, String entityLabel, String action, String field, String oldValue,
            String newValue, String changedBy) {
        this.entityType = entityType;
        this.entityId = entityId;
        this.entityLabel = entityLabel;
        this.action = action;
        this.field = field;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.changedBy = changedBy;
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        this.registered = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getEntityType() {
        return entityType;
    }

    public String getEntityId() {
        return entityId;
    }

    public String getEntityLabel() {
        return entityLabel;
    }

    public String getAction() {
        return action;
    }

    public String getField() {
        return field;
    }

    public String getOldValue() {
        return oldValue;
    }

    public String getNewValue() {
        return newValue;
    }

    public String getChangedBy() {
        return changedBy;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

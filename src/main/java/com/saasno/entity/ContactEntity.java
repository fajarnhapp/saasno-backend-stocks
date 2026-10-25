package com.saasno.entity;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.ContactLabel;
import com.saasno.utility.picker.ContactType;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// One way to reach a person: an email address or a phone number.
@Entity
@Table(schema = "public", name = "contact")
public class ContactEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "person_id", nullable = false, updatable = false)
    private PersonEntity person;

    @Column(length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private ContactType type;

    @Column(length = 256, nullable = false)
    private String value;

    @Column(length = 16, nullable = false)
    @Enumerated(EnumType.STRING)
    private ContactLabel label = ContactLabel.PERSONAL;

    @Column(name = "is_primary", nullable = false)
    private boolean primary = false;

    @Column(name = "verified_at")
    private LocalDateTime verifiedAt;

    @Column(nullable = false)
    private LocalDateTime modificated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public ContactEntity() {

    }

    public ContactEntity(PersonEntity person, ContactType type, String value, ContactLabel label, boolean primary) {
        this.person = person;
        this.type = type;
        this.value = value;
        this.label = label;
        this.primary = primary;
    }

    @PrePersist
    public void beforeInsert() {
        if (this.id == null) {
            this.id = ID.uuid();
        }
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

    public PersonEntity getPerson() {
        return person;
    }

    public ContactType getType() {
        return type;
    }

    public String getValue() {
        return value;
    }

    // A changed address is no longer the one that was verified.
    public void setValue(String value) {
        if (this.value != null && !this.value.equals(value)) {
            this.verifiedAt = null;
        }
        this.value = value;
    }

    public ContactLabel getLabel() {
        return label;
    }

    public void setLabel(ContactLabel label) {
        this.label = label;
    }

    public boolean isPrimary() {
        return primary;
    }

    public void setPrimary(boolean primary) {
        this.primary = primary;
    }

    public LocalDateTime getVerifiedAt() {
        return verifiedAt;
    }

    public void markVerified() {
        this.verifiedAt = LocalDateTime.now();
    }

    public LocalDateTime getModificated() {
        return modificated;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }
}

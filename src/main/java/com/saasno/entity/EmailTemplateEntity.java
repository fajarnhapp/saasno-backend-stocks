package com.saasno.entity;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// The customized text of one email (id = the template key, e.g. PASSWORD_RESET). Without a row the built-in text is used.
@Entity
@Table(schema = "public", name = "email_template")
public class EmailTemplateEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(length = 200, nullable = false)
    private String subject;

    @Column(length = 200, nullable = false)
    private String heading;

    @Column(length = 1000, nullable = false)
    private String intro;

    @Column(name = "button_label", length = 60, nullable = false)
    private String buttonLabel;

    @Column(length = 1000, nullable = false)
    private String note = "";

    @Column(nullable = false)
    private LocalDateTime modificated;

    public EmailTemplateEntity() {

    }

    public EmailTemplateEntity(String id) {
        this.id = id;
    }

    @PrePersist
    @PreUpdate
    public void touch() {
        this.modificated = LocalDateTime.now();
    }

    public String getId() {
        return id;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public String getHeading() {
        return heading;
    }

    public void setHeading(String heading) {
        this.heading = heading;
    }

    public String getIntro() {
        return intro;
    }

    public void setIntro(String intro) {
        this.intro = intro;
    }

    public String getButtonLabel() {
        return buttonLabel;
    }

    public void setButtonLabel(String buttonLabel) {
        this.buttonLabel = buttonLabel;
    }

    public String getNote() {
        return note;
    }

    public void setNote(String note) {
        this.note = note;
    }

    public LocalDateTime getModificated() {
        return modificated;
    }
}

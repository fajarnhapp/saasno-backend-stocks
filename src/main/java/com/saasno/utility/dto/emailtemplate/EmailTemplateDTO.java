package com.saasno.utility.dto.emailtemplate;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// One email as the admin sees it: the text in use, the built-in text, and the {{placeholders}} that can be used in it.
@JsonIgnoreProperties(ignoreUnknown = true)
public class EmailTemplateDTO {
    private String id;
    private String name;
    private String description;
    private List<String> placeholders;
    private boolean customized;
    private String subject;
    private String heading;
    private String intro;
    private String buttonLabel;
    private String note;
    private EmailTemplateDTO defaults;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public List<String> getPlaceholders() {
        return placeholders;
    }

    public void setPlaceholders(List<String> placeholders) {
        this.placeholders = placeholders;
    }

    public boolean isCustomized() {
        return customized;
    }

    public void setCustomized(boolean customized) {
        this.customized = customized;
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

    public EmailTemplateDTO getDefaults() {
        return defaults;
    }

    public void setDefaults(EmailTemplateDTO defaults) {
        this.defaults = defaults;
    }
}

package com.saasno.utility.dto.user;

import java.time.LocalDateTime;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.saasno.utility.dto.person.PersonDTO;
import com.saasno.utility.dto.role.RoleDTO;

@JsonIgnoreProperties(ignoreUnknown = true)
public class UserDTO {
    private String id;
    private String username;
    private String fullName;
    private String email;
    private PersonDTO person;
    private Boolean activationSent;
    private LocalDateTime deletedAt;
    private String deletedBy;
    private Set<RoleDTO> roles;
    private String status;
    private Boolean secure;
    private Boolean twoFactorAuth;
    private Boolean passwordChangeRequired;
    private Boolean twoFactorRequired;
    private Boolean twoFactorSetupRequired;
    private Long avatarVersion;
    private Integer recoveryCodesLeft;
    private LocalDateTime modificated;
    private LocalDateTime registered;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public void setDeletedAt(LocalDateTime deletedAt) {
        this.deletedAt = deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void setDeletedBy(String deletedBy) {
        this.deletedBy = deletedBy;
    }

    public Boolean getActivationSent() {
        return activationSent;
    }

    public void setActivationSent(Boolean activationSent) {
        this.activationSent = activationSent;
    }

    public PersonDTO getPerson() {
        return person;
    }

    public void setPerson(PersonDTO person) {
        this.person = person;
    }

    public Set<RoleDTO> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleDTO> roles) {
        this.roles = roles;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Boolean getSecure() {
        return secure;
    }

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }

    public Long getAvatarVersion() {
        return avatarVersion;
    }

    public void setAvatarVersion(Long avatarVersion) {
        this.avatarVersion = avatarVersion;
    }

    public Integer getRecoveryCodesLeft() {
        return recoveryCodesLeft;
    }

    public void setRecoveryCodesLeft(Integer recoveryCodesLeft) {
        this.recoveryCodesLeft = recoveryCodesLeft;
    }

    public Boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    public void setPasswordChangeRequired(Boolean passwordChangeRequired) {
        this.passwordChangeRequired = passwordChangeRequired;
    }

    public Boolean getTwoFactorRequired() {
        return twoFactorRequired;
    }

    public void setTwoFactorRequired(Boolean twoFactorRequired) {
        this.twoFactorRequired = twoFactorRequired;
    }

    public Boolean getTwoFactorSetupRequired() {
        return twoFactorSetupRequired;
    }

    public void setTwoFactorSetupRequired(Boolean twoFactorSetupRequired) {
        this.twoFactorSetupRequired = twoFactorSetupRequired;
    }

    public Boolean getTwoFactorAuth() {
        return twoFactorAuth;
    }

    public void setTwoFactorAuth(Boolean twoFactorAuth) {
        this.twoFactorAuth = twoFactorAuth;
    }

    public LocalDateTime getModificated() {
        return modificated;
    }

    public void setModificated(LocalDateTime modificated) {
        this.modificated = modificated;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }

    public void setRegistered(LocalDateTime registered) {
        this.registered = registered;
    }
}

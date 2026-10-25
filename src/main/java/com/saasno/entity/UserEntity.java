package com.saasno.entity;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.saasno.utility.cryptography.Argon2;
import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.UserStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.CascadeType;
import jakarta.persistence.OneToOne;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.NamedQueries;
import jakarta.persistence.NamedQuery;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(schema = "public", name = "user", uniqueConstraints = {
        @UniqueConstraint(name = "user_username_uk", columnNames = "username")
})
@NamedQueries({
        @NamedQuery(name = "UserEntity.findByUsername", query = "SELECT u FROM UserEntity u WHERE u.username = :username")
})
public class UserEntity {

    public static final PasswordEncoder passwordEncoder = Argon2.setDefault;


    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(length = 256, nullable = false)
    private String username;

    @Column(length = 256, nullable = false)
    private String password;

    // Who this account belongs to; the person is created and deleted together with the account.
    @JsonIgnore
    @OneToOne(fetch = FetchType.LAZY, cascade = { CascadeType.PERSIST, CascadeType.REMOVE }, optional = false)
    @JoinColumn(name = "person_id", nullable = false, unique = true)
    private PersonEntity person;

    @JsonIgnore
    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(name = "user_role", joinColumns = @JoinColumn(name = "user_id"), inverseJoinColumns = @JoinColumn(name = "role_id"))
    private Set<RoleEntity> roles = new HashSet<>();

    @Column(length = 32, nullable = false)
    @Enumerated(EnumType.STRING)
    private UserStatus status;

    @Column(nullable = false)
    private Boolean secure = false;

    @Column(nullable = false)
    private Boolean twoFactorAuth = false;

    @Column(length = 128)
    private String twoFactorAuthSecret;

    // SHA-256 of each unused recovery code, comma separated (see RecoveryCodeUtility). Null when none.
    @JsonIgnore
    @Column(name = "two_factor_recovery_hashes", columnDefinition = "TEXT")
    private String twoFactorRecoveryHashes;

    @JsonIgnore
    @Column(name = "password_changed_at")
    private LocalDateTime passwordChangedAt;

    // True after the password was set by default (new user or admin reset): the user must choose a new one before using the app.
    @JsonIgnore
    @Column(name = "password_must_change", nullable = false)
    private boolean passwordMustChange = false;

    @JsonIgnore
    @Column(name = "failed_login_count", nullable = false)
    private int failedLoginCount = 0;

    @JsonIgnore
    @Column(name = "locked_until")
    private LocalDateTime lockedUntil;

    // Set when the user was moved to the trash; such a user cannot sign in and is hidden from the list until restored.
    @JsonIgnore
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @JsonIgnore
    @Column(name = "deleted_by", length = 256)
    private String deletedBy;

    @Column(nullable = false)
    private LocalDateTime modificated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public UserEntity() {

    }

    public UserEntity(String username, PersonEntity person, Set<RoleEntity> role) {
        this.username = username;
        this.person = person;
        this.roles.addAll(role);
    }

    @PrePersist
    public void beforeInsert() {
        this.id = ID.uuid();
        // Nobody knows the first password: the user chooses their own through the activation link that is emailed.
        lockPassword();
        this.status = UserStatus.ACTIVED;
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

    public LocalDateTime getDeletedAt() {
        return deletedAt;
    }

    public String getDeletedBy() {
        return deletedBy;
    }

    public void moveToTrash(String by) {
        this.deletedAt = LocalDateTime.now();
        this.deletedBy = by;
    }

    public void restore() {
        this.deletedAt = null;
        this.deletedBy = null;
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

    public PersonEntity getPerson() {
        return person;
    }

    public void setPerson(PersonEntity person) {
        this.person = person;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public Set<RoleEntity> getRoles() {
        return roles;
    }

    public void setRoles(Set<RoleEntity> roles) {
        this.roles = roles;
    }

    public UserStatus getStatus() {
        return status;
    }

    public void setStatus(UserStatus status) {
        this.status = status;
    }

    public Boolean getSecure() {
        return secure;
    }

    public void setSecure(Boolean secure) {
        this.secure = secure;
    }

    public Boolean getTwoFactorAuth() {
        return twoFactorAuth;
    }

    public void setTwoFactorAuth(Boolean twoFactorAuth) {
        this.twoFactorAuth = twoFactorAuth;
    }

    public String getTwoFactorAuthSecret() {
        return twoFactorAuthSecret;
    }

    public void setTwoFactorAuthSecret(String twoFactorAuthSecret) {
        this.twoFactorAuthSecret = twoFactorAuthSecret;
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

    // The stored hash, for the password history. Not a bean getter on purpose: the mappers must never copy it into a DTO.
    public String passwordHash() {
        return password;
    }

    public boolean verifyPassword(String password) {
        return passwordEncoder.matches(password, this.password);
    }

    public UserEntity changePassword(String password) {
        this.password = passwordEncoder.encode(password);
        this.passwordChangedAt = LocalDateTime.now();
        this.passwordMustChange = false;
        return this;
    }

    // Nobody knows this password: the user sets their own through the activation link that was emailed.
    public UserEntity lockPassword() {
        byte[] random = new byte[32];
        new java.security.SecureRandom().nextBytes(random);
        this.password = passwordEncoder.encode(java.util.HexFormat.of().formatHex(random));
        this.passwordChangedAt = null;
        this.passwordMustChange = true;
        return this;
    }

    // Turns two-factor authentication off and forgets its secret and recovery codes; the user sets it up again at the next login if it is required.
    public void resetTwoFactor() {
        this.twoFactorAuth = false;
        this.twoFactorAuthSecret = null;
        this.twoFactorRecoveryHashes = null;
    }

    // Replaces all recovery codes with new ones (given as hashes).
    public void storeRecoveryHashes(List<String> hashes) {
        this.twoFactorRecoveryHashes = hashes == null || hashes.isEmpty() ? null : String.join(",", hashes);
    }

    public void clearRecoveryCodes() {
        this.twoFactorRecoveryHashes = null;
    }

    public int recoveryCodesLeft() {
        return twoFactorRecoveryHashes == null || twoFactorRecoveryHashes.isBlank() ? 0 : twoFactorRecoveryHashes.split(",").length;
    }

    // Uses up a recovery code: true (and the code can never be used again) when it was one of the stored ones.
    public boolean consumeRecoveryCode(String hash) {
        if (twoFactorRecoveryHashes == null || hash == null) {
            return false;
        }
        List<String> remaining = new ArrayList<>(List.of(twoFactorRecoveryHashes.split(",")));
        boolean found = false;
        for (int i = 0; i < remaining.size(); i++) {
            // Compared without stopping at the first difference.
            if (java.security.MessageDigest.isEqual(remaining.get(i).getBytes(), hash.getBytes())) {
                remaining.remove(i);
                found = true;
                break;
            }
        }
        if (found) {
            storeRecoveryHashes(remaining);
        }
        return found;
    }

    @JsonIgnore
    public LocalDateTime getPasswordChangedAt() {
        return passwordChangedAt;
    }

    @JsonIgnore
    public boolean isPasswordMustChange() {
        return passwordMustChange;
    }

    public UserDetails details() {
        return new User(
                this.username,
                "SECRET",
                authorities());
    }

    public List<SimpleGrantedAuthority> authorities() {
        List<SimpleGrantedAuthority> authorities = new ArrayList<>();
        for (RoleEntity role : this.roles) {
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role.getName()));
        }
        return authorities;
    }

    public List<String> roleNames() {
        List<String> list = new ArrayList<>();
        for (RoleEntity role : this.roles) {
            list.add(role.getName());
        }
        return list;
    }

    @JsonIgnore
    public int getFailedLoginCount() {
        return failedLoginCount;
    }

    @JsonIgnore
    public LocalDateTime getLockedUntil() {
        return lockedUntil;
    }

    @JsonIgnore
    public boolean isLocked() {
        return lockedUntil != null && lockedUntil.isAfter(LocalDateTime.now());
    }

    // Records one failure; returns true when the account has just been locked. A lock that has already passed counts from zero.
    public boolean recordFailedLogin(int maxAttempts, int lockMinutes) {
        if (lockedUntil != null && !isLocked()) {
            failedLoginCount = 0;
            lockedUntil = null;
        }
        failedLoginCount++;
        if (failedLoginCount >= maxAttempts) {
            lockedUntil = LocalDateTime.now().plusMinutes(lockMinutes);
            failedLoginCount = 0;
            return true;
        }
        return false;
    }

    public void clearFailedLogin() {
        failedLoginCount = 0;
        lockedUntil = null;
    }

}

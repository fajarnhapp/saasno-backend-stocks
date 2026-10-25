package com.saasno.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import com.saasno.utility.generator.ID;
import com.saasno.utility.picker.ContactType;
import com.saasno.utility.picker.Gender;
import com.saasno.utility.picker.Religion;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

// A human being, apart from any login account (user) or job (employee) they may have.
@Entity
@Table(schema = "public", name = "person")
public class PersonEntity {

    @Id
    @Column(length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "full_name", length = 128, nullable = false)
    private String fullName;

    @Column(length = 64)
    private String nickname;

    @Column(name = "birth_place", length = 64)
    private String birthPlace;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(length = 16)
    @Enumerated(EnumType.STRING)
    private Gender gender;

    @Column(length = 32)
    @Enumerated(EnumType.STRING)
    private Religion religion;

    @OneToMany(mappedBy = "person", fetch = FetchType.LAZY, cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("registered ASC")
    private List<ContactEntity> contacts = new ArrayList<>();

    @Column(nullable = false)
    private LocalDateTime modificated;

    @Column(nullable = false, updatable = false)
    private LocalDateTime registered;

    public PersonEntity() {

    }

    public PersonEntity(String fullName) {
        this.fullName = fullName;
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

    public String getFullName() {
        return fullName;
    }

    public void setFullName(String fullName) {
        this.fullName = fullName;
    }

    public String getNickname() {
        return nickname;
    }

    public void setNickname(String nickname) {
        this.nickname = nickname;
    }

    public String getBirthPlace() {
        return birthPlace;
    }

    public void setBirthPlace(String birthPlace) {
        this.birthPlace = birthPlace;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public void setBirthDate(LocalDate birthDate) {
        this.birthDate = birthDate;
    }

    public Gender getGender() {
        return gender;
    }

    public void setGender(Gender gender) {
        this.gender = gender;
    }

    public Religion getReligion() {
        return religion;
    }

    public void setReligion(Religion religion) {
        this.religion = religion;
    }

    public List<ContactEntity> getContacts() {
        return contacts;
    }

    public LocalDateTime getModificated() {
        return modificated;
    }

    public LocalDateTime getRegistered() {
        return registered;
    }

    // The primary email when it has been confirmed (the link in a verification or activation email was opened), else null.
    public String verifiedPrimaryEmail() {
        return contacts.stream().filter(c -> c.getType() == ContactType.EMAIL).filter(ContactEntity::isPrimary)
                .filter(c -> c.getVerifiedAt() != null).map(ContactEntity::getValue).findFirst().orElse(null);
    }

    public void markPrimaryEmailVerified() {
        contacts.stream().filter(c -> c.getType() == ContactType.EMAIL).filter(ContactEntity::isPrimary).forEach(ContactEntity::markVerified);
    }

    // The primary email, or null when the person has none.
    public String primaryEmail() {
        return contacts.stream().filter(c -> c.getType() == ContactType.EMAIL).filter(ContactEntity::isPrimary)
                .map(ContactEntity::getValue).findFirst().orElse(null);
    }
}

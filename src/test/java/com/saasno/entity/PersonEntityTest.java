package com.saasno.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import com.saasno.service.PasswordPolicyService;
import com.saasno.utility.picker.ContactLabel;
import com.saasno.utility.picker.ContactType;

class PersonEntityTest {

	private PersonEntity personWithEmail(String email, boolean primary) {
		PersonEntity person = new PersonEntity("Budi");
		person.getContacts().add(new ContactEntity(person, ContactType.EMAIL, email, ContactLabel.PERSONAL, primary));
		return person;
	}

	@Test
	void unverifiedPrimaryEmailIsNotUsedForRecovery() {
		PersonEntity person = personWithEmail("budi@example.com", true);
		assertEquals("budi@example.com", person.primaryEmail());
		assertNull(person.verifiedPrimaryEmail());
	}

	@Test
	void verifyingThePrimaryEmailMakesItUsable() {
		PersonEntity person = personWithEmail("budi@example.com", true);
		person.markPrimaryEmailVerified();
		assertEquals("budi@example.com", person.verifiedPrimaryEmail());
	}

	@Test
	void onlyThePrimaryEmailGetsVerified() {
		PersonEntity person = personWithEmail("budi@example.com", true);
		person.getContacts().add(new ContactEntity(person, ContactType.EMAIL, "other@example.com", ContactLabel.WORK, false));
		person.markPrimaryEmailVerified();
		assertNull(person.getContacts().get(1).getVerifiedAt());
	}

	@Test
	void changingTheAddressDropsTheVerification() {
		PersonEntity person = personWithEmail("budi@example.com", true);
		person.markPrimaryEmailVerified();
		person.getContacts().get(0).setValue("budi@example.com");
		assertNotNull(person.getContacts().get(0).getVerifiedAt());
		person.getContacts().get(0).setValue("new@example.com");
		assertNull(person.getContacts().get(0).getVerifiedAt());
	}

	@Test
	void aNewUserStartsLockedWithoutAnyDefaultPassword() {
		UserEntity user = new UserEntity("budi", new PersonEntity("Budi"), java.util.Set.of());
		user.beforeInsert();
		assertFalse(user.verifyPassword(PasswordPolicyService.RETIRED_DEFAULT));
		assertTrue(user.isPasswordMustChange());
	}

	@Test
	void lockedPasswordMatchesNothingAndForcesAChange() {
		UserEntity user = new UserEntity("budi", new PersonEntity("Budi"), java.util.Set.of());
		user.beforeInsert();
		user.lockPassword();
		assertFalse(user.verifyPassword(PasswordPolicyService.RETIRED_DEFAULT));
		assertTrue(user.isPasswordMustChange());
	}
}

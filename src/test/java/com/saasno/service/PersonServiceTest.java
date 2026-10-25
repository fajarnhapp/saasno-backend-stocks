package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

import com.saasno.utility.exception.ApiException;

class PersonServiceTest {

	@Test
	void emailIsTrimmedAndLowercased() {
		assertEquals("budi.santoso@example.com", PersonService.normalizeEmail("  Budi.Santoso@Example.COM "));
	}

	@Test
	void badEmailsAreRejected() {
		for (String bad : new String[] { null, "", "  ", "no-at-sign", "a@b", "a b@c.com", "a@@c.com", "x".repeat(260) + "@c.com" }) {
			assertThrows(ApiException.class, () -> PersonService.normalizeEmail(bad), String.valueOf(bad));
		}
	}

	@Test
	void phoneNumbersAcceptCommonFormats() {
		assertEquals("+62 812-3456-7890", PersonService.normalizePhone(" +62 812-3456-7890 "));
		assertEquals("(021) 555-1234", PersonService.normalizePhone("(021) 555-1234"));
	}

	@Test
	void badPhoneNumbersAreRejected() {
		for (String bad : new String[] { null, "", "abc", "12", "+", "081234567890123456789012345678901" }) {
			assertThrows(ApiException.class, () -> PersonService.normalizePhone(bad), String.valueOf(bad));
		}
	}

	@Test
	void usernameIsAPlainLowercaseName() {
		assertEquals("budi.santoso", UserService.validUsername(" budi.santoso "));
		assertEquals("a-b_c.1", UserService.validUsername("a-b_c.1"));
	}

	@Test
	void usernameCannotBeAnEmailOrOddlyShaped() {
		for (String bad : new String[] { null, "", "ab", "Budi", "budi@example.com", "-budi", "bu di", "x".repeat(65) }) {
			assertThrows(ApiException.class, () -> UserService.validUsername(bad), String.valueOf(bad));
		}
	}
}

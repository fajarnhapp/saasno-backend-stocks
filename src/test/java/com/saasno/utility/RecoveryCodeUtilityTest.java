package com.saasno.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.saasno.entity.UserEntity;

class RecoveryCodeUtilityTest {

	@Test
	void generatesTenDistinctWellFormedCodes() {
		List<String> codes = RecoveryCodeUtility.generate();
		assertEquals(RecoveryCodeUtility.COUNT, codes.size());
		assertEquals(codes.size(), new HashSet<>(codes).size());
		codes.forEach(code -> {
			assertTrue(code.matches("[A-HJ-NP-Z2-9]{5}-[A-HJ-NP-Z2-9]{5}"), code);
			assertTrue(RecoveryCodeUtility.looksLikeRecoveryCode(code));
		});
	}

	@Test
	void anAuthenticatorCodeIsNeverTakenForARecoveryCode() {
		assertFalse(RecoveryCodeUtility.looksLikeRecoveryCode("123456"));
		assertFalse(RecoveryCodeUtility.looksLikeRecoveryCode("1234567890"));
		assertFalse(RecoveryCodeUtility.looksLikeRecoveryCode(null));
		assertFalse(RecoveryCodeUtility.looksLikeRecoveryCode(""));
		assertFalse(RecoveryCodeUtility.looksLikeRecoveryCode("ABCDE-FGHI"));
	}

	@Test
	void hashingIgnoresCaseDashAndSpaces() {
		String hash = RecoveryCodeUtility.hash("ABCDE-FGHJK");
		assertEquals(hash, RecoveryCodeUtility.hash("abcde-fghjk"));
		assertEquals(hash, RecoveryCodeUtility.hash(" ABCDEFGHJK "));
		assertEquals(hash, RecoveryCodeUtility.hash("ABCDE FGHJK"));
		assertEquals(64, hash.length());
	}

	@Test
	void aCodeWorksOnceAndOnlyOnce() {
		List<String> codes = RecoveryCodeUtility.generate();
		UserEntity user = new UserEntity();
		user.storeRecoveryHashes(RecoveryCodeUtility.hashAll(codes));
		assertEquals(10, user.recoveryCodesLeft());

		String hash = RecoveryCodeUtility.hash(codes.get(3));
		assertTrue(user.consumeRecoveryCode(hash));
		assertEquals(9, user.recoveryCodesLeft());
		assertFalse(user.consumeRecoveryCode(hash), "second use of the same code");
		assertFalse(user.consumeRecoveryCode(RecoveryCodeUtility.hash("AAAAA-AAAAA")));
		assertEquals(9, user.recoveryCodesLeft());
	}

	@Test
	void clearingRemovesAllCodes() {
		UserEntity user = new UserEntity();
		user.storeRecoveryHashes(RecoveryCodeUtility.hashAll(RecoveryCodeUtility.generate()));
		user.clearRecoveryCodes();
		assertEquals(0, user.recoveryCodesLeft());
		assertFalse(user.consumeRecoveryCode(RecoveryCodeUtility.hash("AAAAA-AAAAA")));
	}

}

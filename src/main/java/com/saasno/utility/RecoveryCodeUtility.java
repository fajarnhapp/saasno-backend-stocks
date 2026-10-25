package com.saasno.utility;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

// One-time recovery codes for two-step verification (for when the authenticator app is lost). A code is 10 characters shown as
// XXXXX-XXXXX from an alphabet without look-alike characters (no 0/O, 1/I). Only a SHA-256 of each code is stored; 50 random bits
// per code make guessing infeasible, and the account lockout limits attempts anyway.
public final class RecoveryCodeUtility {

	public static final int COUNT = 10;

	private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
	private static final Pattern FORMAT = Pattern.compile("^[A-Z2-9]{5}-?[A-Z2-9]{5}$");
	private static final SecureRandom RANDOM = new SecureRandom();

	private RecoveryCodeUtility() {
	}

	public static List<String> generate() {
		List<String> codes = new ArrayList<>();
		for (int i = 0; i < COUNT; i++) {
			StringBuilder code = new StringBuilder();
			for (int j = 0; j < 10; j++) {
				if (j == 5) {
					code.append('-');
				}
				code.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
			}
			codes.add(code.toString());
		}
		return codes;
	}

	// A 6-digit authenticator code never looks like this, so the login can tell the two apart.
	public static boolean looksLikeRecoveryCode(String input) {
		return input != null && FORMAT.matcher(normalize(input)).matches() && !normalize(input).chars().allMatch(Character::isDigit);
	}

	public static String hash(String code) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256").digest(normalize(code).replace("-", "").getBytes(StandardCharsets.UTF_8));
			return HexFormat.of().formatHex(digest);
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public static List<String> hashAll(List<String> codes) {
		return codes.stream().map(RecoveryCodeUtility::hash).toList();
	}

	private static String normalize(String input) {
		return input.trim().toUpperCase(Locale.ROOT).replace(" ", "");
	}

}

package com.saasno.service;

import java.time.LocalDateTime;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.PasswordHistoryEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.PasswordHistoryRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.password.PasswordPolicyDTO;
import com.saasno.utility.exception.ApiException;

// The client sends only the SHA-256 hash of a password, so length and complexity can only be checked in the client (see
// PasswordPolicyDTO). Here the backend enforces what a hash allows: format, not a retired shared default, not the current password,
// and the lifecycle (must change after a reset, expiry).
@Service
public class PasswordPolicyService {

	static final int DEFAULT_MIN_LENGTH = 8;
	static final int DEFAULT_EXPIRY_DAYS = 0;
	static final int DEFAULT_HISTORY_COUNT = 5;
	static final int MAX_HISTORY = 24;
	static final int DEFAULT_RESET_MINUTES = 30;
	static final int DEFAULT_RESET_MAX_PER_HOUR = 3;

	// The shared password that new accounts used to get. It no longer exists, but it is public in old versions, so nobody may choose it.
	public static final String RETIRED_DEFAULT = "ff7bd97b1a7789ddd2775122fd6817f3173672da9f802ceec57f284325bf589f";

	private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private PasswordHistoryRepository passwordHistoryRepository;

	public PasswordPolicyDTO policy() {
		return new PasswordPolicyDTO(
				integer(ConfigurationKey.PASSWORD_MIN_LENGTH, DEFAULT_MIN_LENGTH, 1),
				!"false".equalsIgnoreCase(value(ConfigurationKey.PASSWORD_REQUIRE_COMPLEXITY)));
	}

	public int resetMinutes() {
		return integer(ConfigurationKey.PASSWORD_RESET_MINUTES, DEFAULT_RESET_MINUTES, 1);
	}

	public int resetMaxPerHour() {
		return integer(ConfigurationKey.PASSWORD_RESET_MAX_PER_HOUR, DEFAULT_RESET_MAX_PER_HOUR, 1);
	}

	public boolean isExpired(UserEntity user) {
		int days = integer(ConfigurationKey.PASSWORD_EXPIRY_DAYS, DEFAULT_EXPIRY_DAYS, 0);
		return isExpired(user.getPasswordChangedAt(), days, LocalDateTime.now());
	}

	public boolean isChangeRequired(UserEntity user) {
		return user.isPasswordMustChange() || isExpired(user);
	}

	// 0 days = expiry off; a password with no recorded change time is never treated as expired.
	static boolean isExpired(LocalDateTime changedAt, int expiryDays, LocalDateTime now) {
		if (expiryDays <= 0 || changedAt == null) {
			return false;
		}
		return changedAt.plusDays(expiryDays).isBefore(now);
	}

	// How many of the latest passwords of an account cannot be chosen again; 0 = off.
	public int historyCount() {
		return Math.min(integer(ConfigurationKey.PASSWORD_HISTORY_COUNT, DEFAULT_HISTORY_COUNT, 0), MAX_HISTORY);
	}

	// A new password (hash) chosen by the user, for a user who already has a password: not the current one, nor one of the latest ones.
	public void validateNewPassword(String newHash, UserEntity user) {
		validateFormat(newHash);
		if (user == null) {
			return;
		}
		if (user.verifyPassword(newHash)) {
			throw new ApiException("400", "New password must be different from the current password");
		}
		validateNotUsedBefore(newHash, user);
	}

	// A forgotten password is replaced through a link, so there the current password is not compared, only the ones used before.
	public void validateNotUsedBefore(String newHash, UserEntity user) {
		int keep = historyCount();
		if (keep <= 0) {
			return;
		}
		for (PasswordHistoryEntity used : passwordHistoryRepository.latest(user.getId(), keep)) {
			if (UserEntity.passwordEncoder.matches(newHash, used.getPasswordHash())) {
				throw new ApiException("400", "This password was used before; choose a different one");
			}
		}
	}

	// Called after a user chose a new password: remembers it, and forgets the passwords that are older than the latest ones.
	public void remember(UserEntity user) {
		int keep = historyCount();
		if (keep <= 0 || user.passwordHash() == null) {
			return;
		}
		passwordHistoryRepository.store(new PasswordHistoryEntity(user.getId(), user.passwordHash()));
		passwordHistoryRepository.keepLatest(user.getId(), keep);
	}

	public void validateFormat(String hash) {
		if (hash == null || !SHA256_HEX.matcher(hash).matches()) {
			throw new ApiException("400", "Invalid password");
		}
		if (RETIRED_DEFAULT.equalsIgnoreCase(hash)) {
			throw new ApiException("400", "Choose a password other than the default one");
		}
	}

	private String value(ConfigurationKey key) {
		return configurationRepository.get(key);
	}

	private int integer(ConfigurationKey key, int fallback, int min) {
		String value = value(key);
		try {
			int parsed = value == null ? fallback : Integer.parseInt(value.trim());
			return parsed < min ? fallback : parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}

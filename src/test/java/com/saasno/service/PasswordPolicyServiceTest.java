package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.PasswordHistoryEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.PasswordHistoryRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.exception.ApiException;

class PasswordPolicyServiceTest {

	private static final String HASH_A = "a".repeat(64);
	private static final String HASH_B = "b".repeat(64);

	private ConfigurationRepository configuration;
	private PasswordHistoryRepository history;
	private PasswordPolicyService service;

	@BeforeEach
	void setUp() {
		configuration = mock(ConfigurationRepository.class);
		service = new PasswordPolicyService();
		ReflectionTestUtils.setField(service, "configurationRepository", configuration);
		history = mock(PasswordHistoryRepository.class);
		ReflectionTestUtils.setField(service, "passwordHistoryRepository", history);
	}

	@Test
	void policyFallsBackToDefaultsWhenNothingIsConfigured() {
		var policy = service.policy();
		assertEquals(8, policy.minLength());
		assertTrue(policy.requireComplexity());
		assertEquals(30, service.resetMinutes());
	}

	@Test
	void policyReadsConfigurationAndIgnoresInvalidValues() {
		when(configuration.get(ConfigurationKey.PASSWORD_MIN_LENGTH)).thenReturn("12");
		when(configuration.get(ConfigurationKey.PASSWORD_REQUIRE_COMPLEXITY)).thenReturn("false");
		when(configuration.get(ConfigurationKey.PASSWORD_RESET_MINUTES)).thenReturn("abc");

		var policy = service.policy();
		assertEquals(12, policy.minLength());
		assertFalse(policy.requireComplexity());
		assertEquals(30, service.resetMinutes());
	}

	@Test
	void expiryIsOffWhenZeroOrWhenNoChangeTimeIsKnown() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
		assertFalse(PasswordPolicyService.isExpired(now.minusDays(1000), 0, now));
		assertFalse(PasswordPolicyService.isExpired(null, 90, now));
	}

	@Test
	void expiryComparesAgeWithConfiguredDays() {
		LocalDateTime now = LocalDateTime.of(2026, 10, 21, 12, 0);
		assertFalse(PasswordPolicyService.isExpired(now.minusDays(89), 90, now));
		assertTrue(PasswordPolicyService.isExpired(now.minusDays(91), 90, now));
	}

	@Test
	void changeIsRequiredForADefaultPasswordAndForAnExpiredOne() {
		when(configuration.get(ConfigurationKey.PASSWORD_EXPIRY_DAYS)).thenReturn("30");

		UserEntity fresh = new UserEntity();
		fresh.beforeInsert();
		assertTrue(service.isChangeRequired(fresh));

		fresh.changePassword(HASH_A);
		assertFalse(service.isChangeRequired(fresh));

		ReflectionTestUtils.setField(fresh, "passwordChangedAt", LocalDateTime.now().minusDays(31));
		assertTrue(service.isChangeRequired(fresh));

		fresh.lockPassword();
		assertTrue(fresh.isPasswordMustChange());
	}

	@Test
	void rejectsAnythingThatIsNotASha256Hash() {
		assertThrows(ApiException.class, () -> service.validateFormat(null));
		assertThrows(ApiException.class, () -> service.validateFormat("short"));
		assertThrows(ApiException.class, () -> service.validateFormat("z".repeat(64)));
		service.validateFormat(HASH_A);
	}

	@Test
	void rejectsTheDefaultPassword() {
		assertThrows(ApiException.class, () -> service.validateFormat(PasswordPolicyService.RETIRED_DEFAULT));
	}

	@Test
	void rejectsReusingTheCurrentPassword() {
		UserEntity user = new UserEntity();
		user.changePassword(HASH_A);

		assertThrows(ApiException.class, () -> service.validateNewPassword(HASH_A, user));
		service.validateNewPassword(HASH_B, user);
	}

	private UserEntity userWithPassword(String hash) {
		UserEntity user = new UserEntity();
		ReflectionTestUtils.setField(user, "id", "user-id");
		user.changePassword(hash);
		return user;
	}

	@Test
	void rejectsOneOfTheLatestPasswordsButNotAnOlderOne() {
		UserEntity user = userWithPassword(HASH_A);
		when(history.latest("user-id", 5)).thenReturn(java.util.List.of(new PasswordHistoryEntity("user-id", UserEntity.passwordEncoder.encode("d".repeat(64)))));

		ApiException e = assertThrows(ApiException.class, () -> service.validateNewPassword("d".repeat(64), user));
		assertEquals("This password was used before; choose a different one", e.getMessage());
		service.validateNewPassword(HASH_B, user);
	}

	@Test
	void historyOffMeansNothingIsCheckedOrKept() {
		when(configuration.get(ConfigurationKey.PASSWORD_HISTORY_COUNT)).thenReturn("0");
		UserEntity user = userWithPassword(HASH_A);

		service.validateNewPassword(HASH_B, user);
		service.remember(user);

		verify(history, never()).latest(any(), org.mockito.ArgumentMatchers.anyInt());
		verify(history, never()).store(any());
	}

	@Test
	void rememberKeepsTheNewestPasswordsOnly() {
		when(configuration.get(ConfigurationKey.PASSWORD_HISTORY_COUNT)).thenReturn("3");
		UserEntity user = userWithPassword(HASH_A);

		service.remember(user);

		verify(history).store(any(PasswordHistoryEntity.class));
		verify(history).keepLatest("user-id", 3);
	}

	@Test
	void historyCountFallsBackAndIsCapped() {
		assertEquals(5, service.historyCount());
		when(configuration.get(ConfigurationKey.PASSWORD_HISTORY_COUNT)).thenReturn("500");
		assertEquals(PasswordPolicyService.MAX_HISTORY, service.historyCount());
		when(configuration.get(ConfigurationKey.PASSWORD_HISTORY_COUNT)).thenReturn("abc");
		assertEquals(5, service.historyCount());
	}

}

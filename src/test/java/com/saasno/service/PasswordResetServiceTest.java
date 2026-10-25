package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.PasswordHistoryEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.PasswordHistoryRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.picker.UserStatus;

class PasswordResetServiceTest {

	private static final String HASH = "c".repeat(64);

	private StringRedisTemplate redis;
	private ValueOperations<String, String> values;
	private UserRepository userRepository;
	private SessionService sessionService;
	private PasswordHistoryRepository passwordHistoryRepository;
	private PasswordResetService service;

	@SuppressWarnings("unchecked")
	@BeforeEach
	void setUp() {
		redis = mock(StringRedisTemplate.class);
		values = mock(ValueOperations.class);
		when(redis.opsForValue()).thenReturn(values);
		userRepository = mock(UserRepository.class);
		sessionService = mock(SessionService.class);

		service = new PasswordResetService();
		ReflectionTestUtils.setField(service, "redis", redis);
		ReflectionTestUtils.setField(service, "userRepository", userRepository);
		ReflectionTestUtils.setField(service, "sessionService", sessionService);
		passwordHistoryRepository = mock(PasswordHistoryRepository.class);
		PasswordPolicyService policy = new PasswordPolicyService();
		ReflectionTestUtils.setField(policy, "configurationRepository", mock(ConfigurationRepository.class));
		ReflectionTestUtils.setField(policy, "passwordHistoryRepository", passwordHistoryRepository);
		ReflectionTestUtils.setField(service, "passwordPolicyService", policy);
		ReflectionTestUtils.setField(service, "mailService", mock(MailService.class));
		ReflectionTestUtils.setField(service, "appSettingService", mock(AppSettingService.class));
	}

	@Test
	void tokensAreLongRandomAndUrlSafe() {
		String a = PasswordResetService.newToken();
		String b = PasswordResetService.newToken();
		assertNotEquals(a, b);
		assertTrue(a.length() >= 43);
		assertTrue(a.matches("[A-Za-z0-9_-]+"));
	}

	@Test
	void onlyTheHashOfATokenIsStored() {
		String hash = PasswordResetService.hash("abc");
		assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hash);
	}

	@Test
	void resetRejectsAMalformedTokenWithoutTouchingRedis() {
		assertThrows(ApiException.class, () -> service.reset("short", HASH));
		assertThrows(ApiException.class, () -> service.reset(null, HASH));
		verify(values, never()).getAndDelete(any());
	}

	@Test
	void resetRejectsAnUnknownOrUsedToken() {
		when(values.getAndDelete(any())).thenReturn(null);
		ApiException e = assertThrows(ApiException.class, () -> service.reset("x".repeat(43), HASH));
		assertEquals(PasswordResetService.INVALID_LINK, e.getMessage());
		verify(userRepository, never()).store(any());
	}

	@Test
	void resetRejectsTheDefaultPasswordBeforeConsumingTheToken() {
		assertThrows(ApiException.class, () -> service.reset("x".repeat(43), PasswordPolicyService.RETIRED_DEFAULT));
		verify(values, never()).getAndDelete(any());
	}

	@Test
	void resetSetsThePasswordAndSignsTheUserOut() {
		UserEntity user = new UserEntity("user@example.com", new com.saasno.entity.PersonEntity("User"), java.util.Set.of());
		user.beforeInsert();
		user.setUsername("user@example.com");
		user.setStatus(UserStatus.ACTIVED);
		when(values.getAndDelete(any())).thenReturn("user-id");
		when(userRepository.find("user-id")).thenReturn(user);

		service.reset("x".repeat(43), HASH);

		assertTrue(user.verifyPassword(HASH));
		assertEquals(false, user.isPasswordMustChange());
		verify(userRepository).store(user);
		verify(sessionService).deleteAllByUsername("user@example.com");
		verify(passwordHistoryRepository).store(any(PasswordHistoryEntity.class));
		verify(passwordHistoryRepository).keepLatest(user.getId(), PasswordPolicyService.DEFAULT_HISTORY_COUNT);
	}

	@Test
	void aPasswordUsedBeforeIsRefusedAndTheLinkStaysUsable() {
		UserEntity user = new UserEntity("user@example.com", new com.saasno.entity.PersonEntity("User"), java.util.Set.of());
		user.beforeInsert();
		user.setStatus(UserStatus.ACTIVED);
		when(values.get(any())).thenReturn("user-id");
		when(userRepository.find("user-id")).thenReturn(user);
		when(passwordHistoryRepository.latest("user-id", PasswordPolicyService.DEFAULT_HISTORY_COUNT))
				.thenReturn(java.util.List.of(historyOf("user-id", HASH)));
		ReflectionTestUtils.setField(user, "id", "user-id");

		ApiException e = assertThrows(ApiException.class, () -> service.reset("x".repeat(43), HASH));
		assertEquals("This password was used before; choose a different one", e.getMessage());
		verify(values, never()).getAndDelete(any());
		verify(userRepository, never()).store(any());
	}

	private static PasswordHistoryEntity historyOf(String userId, String hash) {
		return new PasswordHistoryEntity(userId, UserEntity.passwordEncoder.encode(hash));
	}

	@Test
	void resetRefusesAnInactiveAccount() {
		UserEntity user = new UserEntity();
		user.setStatus(UserStatus.INACTIVED);
		when(values.getAndDelete(any())).thenReturn("user-id");
		when(userRepository.find("user-id")).thenReturn(user);

		assertThrows(ApiException.class, () -> service.reset("x".repeat(43), HASH));
		verify(userRepository, never()).store(any());
	}

	@Test
	void forgotDoesNothingForABlankUsername() {
		service.forgot("  ");
		service.forgot(null);
		verify(userRepository, never()).findByLogin(any());
	}

}

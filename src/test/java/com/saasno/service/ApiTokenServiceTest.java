package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.ApiTokenEntity;
import com.saasno.entity.PersonEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ApiTokenRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.token.ApiTokenDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.picker.UserStatus;

class ApiTokenServiceTest {

	private ApiTokenRepository tokens;
	private UserRepository users;
	private PasswordPolicyService passwordPolicy;
	private ApiTokenService service;
	private UserEntity user;

	@BeforeEach
	void setUp() {
		tokens = mock(ApiTokenRepository.class);
		users = mock(UserRepository.class);
		passwordPolicy = mock(PasswordPolicyService.class);
		SessionUtility session = mock(SessionUtility.class);
		when(session.getUsername()).thenReturn("budi");
		service = new ApiTokenService();
		ReflectionTestUtils.setField(service, "apiTokenRepository", tokens);
		ReflectionTestUtils.setField(service, "userRepository", users);
		ReflectionTestUtils.setField(service, "sessionUtility", session);
		ReflectionTestUtils.setField(service, "passwordPolicyService", passwordPolicy);
		ReflectionTestUtils.setField(service, "changeLogService", mock(ChangeLogService.class));

		user = new UserEntity("budi", new PersonEntity("Budi"), Set.of());
		user.beforeInsert();
		user.setStatus(UserStatus.ACTIVED);
		when(users.find(user.getId())).thenReturn(user);
		when(users.findByUsername("budi")).thenReturn(user);
	}

	private ApiTokenEntity stored(String token, LocalDateTime expires) {
		return new ApiTokenEntity(user.getId(), "ci", PasswordResetService.hash(token), token.substring(0, 10), expires);
	}

	@Test
	void tokensLookLikeTokensAndAreRandom() {
		String a = ApiTokenService.newToken();
		String b = ApiTokenService.newToken();
		assertTrue(a.startsWith("sn_") && a.length() > 40);
		assertTrue(ApiTokenService.looksLikeToken(a));
		assertFalse(ApiTokenService.looksLikeToken("abc:123"));
		assertFalse(ApiTokenService.looksLikeToken(null));
		assertFalse(a.equals(b));
	}

	@Test
	void aValidTokenGivesASessionOfItsOwner() {
		String token = ApiTokenService.newToken();
		ApiTokenEntity entity = stored(token, LocalDateTime.now().plusDays(1));
		when(tokens.findByHash(PasswordResetService.hash(token))).thenReturn(entity);

		SessionEntity session = service.authenticate(token);

		assertEquals("budi", session.getUsername());
		assertTrue(session.isApiToken());
		assertNotNull(entity.getLastUsedAt());
	}

	@Test
	void anUnknownExpiredOrRevokedTokenIsRefused() {
		assertThrows(ApiException.class, () -> service.authenticate("sn_unknown"));

		String expired = ApiTokenService.newToken();
		when(tokens.findByHash(PasswordResetService.hash(expired))).thenReturn(stored(expired, LocalDateTime.now().minusMinutes(1)));
		assertThrows(ApiException.class, () -> service.authenticate(expired));

		String revoked = ApiTokenService.newToken();
		ApiTokenEntity entity = stored(revoked, LocalDateTime.now().plusDays(1));
		entity.setRevokedAt(LocalDateTime.now());
		when(tokens.findByHash(PasswordResetService.hash(revoked))).thenReturn(entity);
		assertThrows(ApiException.class, () -> service.authenticate(revoked));
	}

	@Test
	void aTokenOfAnInactiveOrTrashedUserIsRefused() {
		String token = ApiTokenService.newToken();
		when(tokens.findByHash(PasswordResetService.hash(token))).thenReturn(stored(token, LocalDateTime.now().plusDays(1)));

		user.setStatus(UserStatus.BLOCKED);
		assertThrows(ApiException.class, () -> service.authenticate(token));

		user.setStatus(UserStatus.ACTIVED);
		user.moveToTrash("admin");
		assertThrows(ApiException.class, () -> service.authenticate(token));
	}

	@Test
	void lastUsedIsNotRewrittenOnEveryRequest() {
		String token = ApiTokenService.newToken();
		ApiTokenEntity entity = stored(token, LocalDateTime.now().plusDays(1));
		LocalDateTime recent = LocalDateTime.now().minusSeconds(5);
		entity.setLastUsedAt(recent);
		when(tokens.findByHash(PasswordResetService.hash(token))).thenReturn(entity);
		service.authenticate(token);
		assertEquals(recent, entity.getLastUsedAt());
	}

	@Test
	void creatingATokenShowsItOnceAndStoresOnlyItsHash() {
		when(tokens.usable(any(), any())).thenReturn(List.of());
		ApiTokenDTO request = new ApiTokenDTO();
		request.setName("deploy robot");
		request.setExpiresInDays(30);

		ApiTokenDTO created = service.create(request).getData();

		ArgumentCaptor<ApiTokenEntity> saved = ArgumentCaptor.forClass(ApiTokenEntity.class);
		verify(tokens).store(saved.capture());
		assertTrue(created.getToken().startsWith("sn_"));
		assertEquals(PasswordResetService.hash(created.getToken()), ReflectionTestUtils.getField(saved.getValue(), "tokenHash"));
		assertTrue(saved.getValue().getExpiresAt().isAfter(LocalDateTime.now().plusDays(29)));
	}

	@Test
	void badNamesAndValiditiesAreRefused() {
		when(tokens.usable(any(), any())).thenReturn(List.of());
		ApiTokenDTO noName = new ApiTokenDTO();
		noName.setName(" ");
		assertThrows(ApiException.class, () -> service.create(noName));
		ApiTokenDTO tooLong = new ApiTokenDTO();
		tooLong.setName("x");
		tooLong.setExpiresInDays(ApiTokenService.MAX_DAYS + 1);
		assertThrows(ApiException.class, () -> service.create(tooLong));
		verify(tokens, never()).store(any());
	}

	@Test
	void thereIsALimitOnTokensPerUser() {
		when(tokens.usable(any(), any())).thenReturn(java.util.Collections.nCopies(ApiTokenService.MAX_TOKENS_PER_USER, mock(ApiTokenEntity.class)));
		ApiTokenDTO request = new ApiTokenDTO();
		request.setName("one more");
		assertThrows(ApiException.class, () -> service.create(request));
	}

	@Test
	void youCannotRevokeSomeoneElsesToken() {
		ApiTokenEntity foreign = new ApiTokenEntity("someone-else", "x", "h", "sn_xxxxxxx", LocalDateTime.now().plusDays(1));
		when(tokens.find("t1")).thenReturn(foreign);
		assertThrows(ApiException.class, () -> service.revoke("t1"));
		assertNull(foreign.getRevokedAt());
	}

	@Test
	void aTokenCannotManageTokensOrCredentials() {
		assertFalse(SessionService.allowedWithApiToken("/api/token"));
		assertFalse(SessionService.allowedWithApiToken("/api/user/change-password"));
		assertFalse(SessionService.allowedWithApiToken("/api/user/otp/enable"));
		assertFalse(SessionService.allowedWithApiToken("/api/user/verify-password"));
		assertFalse(SessionService.allowedWithApiToken("/api/person/me/export"));
		assertFalse(SessionService.allowedWithApiToken("/api/privacy/delete-request"));
		assertTrue(SessionService.allowedWithApiToken("/api/user"));
		assertTrue(SessionService.allowedWithApiToken("/api/user/me"));
	}
}

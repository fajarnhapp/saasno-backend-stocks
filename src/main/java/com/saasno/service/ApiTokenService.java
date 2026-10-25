package com.saasno.service;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.ApiTokenEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ApiTokenRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.picker.UserStatus;

import com.saasno.utility.dto.token.ApiTokenDTO;

import jakarta.transaction.Transactional;

// API tokens: long-lived credentials another system sends as "Authorization: Bearer sn_...". A token acts as the user who created it, with the
// same permissions; it cannot manage tokens or change passwords or two-factor settings. Only the SHA-256 of a token is stored.
@Service
public class ApiTokenService {

	public static final String PREFIX = "sn_";
	static final int MAX_TOKENS_PER_USER = 10;
	static final int MAX_DAYS = 365;
	static final int DEFAULT_DAYS = 90;
	// last_used_at is written at most this often, so a busy integration does not turn every request into a write.
	private static final Duration USED_RESOLUTION = Duration.ofMinutes(1);
	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private ApiTokenRepository apiTokenRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private PasswordPolicyService passwordPolicyService;

	@Autowired
	private ChangeLogService changeLogService;

	static boolean looksLikeToken(String bearer) {
		return bearer != null && bearer.startsWith(PREFIX);
	}

	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	// A session standing in for the token owner, for this one request (nothing is stored).
	@Transactional
	public SessionEntity authenticate(String token) {
		ApiTokenEntity entity = apiTokenRepository.findByHash(PasswordResetService.hash(token));
		LocalDateTime now = LocalDateTime.now();
		if (entity == null || !entity.isUsable(now)) {
			throw new ApiException("401", "The API token is invalid or has expired");
		}
		UserEntity user = userRepository.find(entity.getUserId());
		if (user == null || user.getDeletedAt() != null || user.getStatus() != UserStatus.ACTIVED || user.isLocked()) {
			throw new ApiException("401", "The API token is invalid or has expired");
		}
		if (entity.getLastUsedAt() == null || entity.getLastUsedAt().plus(USED_RESOLUTION).isBefore(now)) {
			entity.setLastUsedAt(now);
		}
		SessionEntity session = new SessionEntity(user, 60);
		session.setApiToken(true);
		session.setPasswordChangeRequired(passwordPolicyService.isChangeRequired(user));
		return session;
	}

	@Transactional
	public ResponseModel<List<ApiTokenDTO>> mine() {
		List<ApiTokenDTO> list = apiTokenRepository.usable(currentUser().getId(), LocalDateTime.now()).stream().map(ApiTokenService::describe).toList();
		return new ResponseModel<List<ApiTokenDTO>>().success(list);
	}

	@Transactional
	public ResponseModel<ApiTokenDTO> create(ApiTokenDTO request) {
		UserEntity user = currentUser();
		String name = request.getName() == null ? "" : request.getName().trim();
		if (name.isEmpty() || name.length() > 100) {
			throw new ApiException("400", "Name is required (at most 100 characters)");
		}
		int days = request.getExpiresInDays() == null ? DEFAULT_DAYS : request.getExpiresInDays();
		if (days < 1 || days > MAX_DAYS) {
			throw new ApiException("400", "Validity must be between 1 and " + MAX_DAYS + " days");
		}
		LocalDateTime now = LocalDateTime.now();
		if (apiTokenRepository.usable(user.getId(), now).size() >= MAX_TOKENS_PER_USER) {
			throw new ApiException("409", "You already have " + MAX_TOKENS_PER_USER + " tokens; revoke one first");
		}

		String token = newToken();
		ApiTokenEntity entity = new ApiTokenEntity(user.getId(), name, PasswordResetService.hash(token), token.substring(0, 10), now.plusDays(days));
		apiTokenRepository.store(entity);
		changeLogService.created("API_TOKEN", entity.getId(), user.getUsername() + " / " + name);

		ApiTokenDTO dto = describe(entity);
		dto.setToken(token);
		return new ResponseModel<ApiTokenDTO>().success(dto);
	}

	@Transactional
	public ResponseModel<Void> revoke(String id) {
		UserEntity user = currentUser();
		ApiTokenEntity entity = apiTokenRepository.find(id);
		// Someone else's token looks the same as a missing one.
		if (entity == null || !entity.getUserId().equals(user.getId()) || entity.getRevokedAt() != null) {
			throw new ApiException("404", "Token does not exist");
		}
		entity.setRevokedAt(LocalDateTime.now());
		changeLogService.event("API_TOKEN", entity.getId(), user.getUsername() + " / " + entity.getName(), "revoked");
		return new ResponseModel<Void>().success();
	}

	private UserEntity currentUser() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		return user;
	}

	private static ApiTokenDTO describe(ApiTokenEntity entity) {
		ApiTokenDTO dto = new ApiTokenDTO();
		dto.setId(entity.getId());
		dto.setName(entity.getName());
		dto.setPrefix(entity.getPrefix());
		dto.setExpiresAt(entity.getExpiresAt());
		dto.setLastUsedAt(entity.getLastUsedAt());
		dto.setRegistered(entity.getRegistered());
		return dto;
	}
}

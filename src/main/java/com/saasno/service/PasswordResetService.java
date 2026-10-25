package com.saasno.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BrandColor;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.picker.UserStatus;

import jakarta.transaction.Transactional;

// Forgot-password by email. The link carries a random one-time token; only its SHA-256 is kept (in Redis, with an expiry), so a
// leaked Redis dump cannot be used to reset anyone's password. The email goes to the primary email address of the account; the username or that address can be typed in the form.
@Service
public class PasswordResetService {

	static final String INVALID_LINK = "The reset link is invalid or has expired";

	private static final String TOKEN_PREFIX = "pwreset:token:";
	private static final String USER_PREFIX = "pwreset:user:";
	private static final String RATE_PREFIX = "pwreset:rate:";
		private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
	private static final Duration ACTIVATION_TTL = Duration.ofHours(72);
	private static final SecureRandom RANDOM = new SecureRandom();

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private PasswordPolicyService passwordPolicyService;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private MailService mailService;

	@Autowired
	private AppSettingService appSettingService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	// Always completes the same way, whether or not the account exists, so the form cannot be used to find valid usernames.
	@Transactional
	public void forgot(String rawUsername) {
		String username = rawUsername == null ? "" : rawUsername.trim();
		if (username.isEmpty() || username.length() > 256 || !withinRateLimit(username)) {
			return;
		}

		UserEntity user = userRepository.findByLogin(username);
		// Only a verified address receives the link, so an address someone typed in for another person cannot be used to take over an account.
		String email = user == null || user.getPerson() == null ? null : user.getPerson().verifiedPrimaryEmail();
		if (user == null || user.getStatus() != UserStatus.ACTIVED || email == null || !EMAIL.matcher(email).matches()) {
			return;
		}

		if (!issueLink(user, email, false)) {
			Log.warn("Forgot password requested but email is not configured (set MAIL_HOST on the Configuration page and FRONTEND_URL)");
		}
	}

	// For a new account, or one whose password an admin reset: the user gets a link to choose their own password. Returns false (nothing
	// sent) when the account has no primary email or email is not configured; the account then stays locked until a link can be sent.
	@Transactional
	public boolean sendActivation(UserEntity user) {
		String email = user.getPerson() == null ? null : user.getPerson().primaryEmail();
		return email != null && issueLink(user, email, true);
	}

	// Creates the one-time link and emails it. False when email is not set up.
	private boolean issueLink(UserEntity user, String email, boolean activation) {
		String frontendUrl = configurationRepository.get(ConfigurationKey.FRONTEND_URL);
		if (!mailService.configured() || frontendUrl == null || frontendUrl.isBlank()) {
			return false;
		}

		String token = newToken();
		Duration ttl = activation ? ACTIVATION_TTL : Duration.ofMinutes(passwordPolicyService.resetMinutes());
		// Only the latest link works.
		String previous = redis.opsForValue().getAndDelete(USER_PREFIX + user.getId());
		if (previous != null) {
			redis.delete(TOKEN_PREFIX + previous);
		}
		String hash = hash(token);
		redis.opsForValue().set(TOKEN_PREFIX + hash, user.getId(), ttl);
		redis.opsForValue().set(USER_PREFIX + user.getId(), hash, ttl);

		String link = frontendUrl.trim().replaceAll("/+$", "") + "/reset-password?token=" + token;
		String appName = appSettingService.appName();
		String color = BrandColor.hex(appSettingService.hue(), appSettingService.chroma());
		String lifetime = activation ? ttl.toHours() + " hours" : ttl.toMinutes() + " minutes";
		EmailTemplateService.Text text = emailTemplateService.resolve(
				activation ? EmailTemplateService.ACCOUNT_ACTIVATION : EmailTemplateService.PASSWORD_RESET,
				Map.of("appName", appName, "username", user.getUsername(), "lifetime", lifetime));
		mailService.sendAsync(email, EmailTemplate.build(appName, color, text.subject(), text.heading(), text.intro(), List.of(),
				text.buttonLabel(), link, text.note()), appName);
		return true;
	}

	@Transactional
	public void reset(String token, String passwordHash) {
		if (token == null || token.length() < 20 || token.length() > 128) {
			throw new ApiException("400", INVALID_LINK);
		}
		passwordPolicyService.validateFormat(passwordHash);

		// A password that was used before is refused while the link is still unused, so the person can try another one.
		String waiting = redis.opsForValue().get(TOKEN_PREFIX + hash(token));
		UserEntity candidate = waiting == null ? null : userRepository.find(waiting);
		if (candidate != null) {
			passwordPolicyService.validateNotUsedBefore(passwordHash, candidate);
		}

		// Read-and-delete in one step, so a link works only once even when two requests arrive together.
		String userId = redis.opsForValue().getAndDelete(TOKEN_PREFIX + hash(token));
		if (userId == null) {
			throw new ApiException("400", INVALID_LINK);
		}
		redis.delete(USER_PREFIX + userId);

		UserEntity user = userRepository.find(userId);
		if (user == null || user.getStatus() != UserStatus.ACTIVED || user.getDeletedAt() != null) {
			throw new ApiException("400", INVALID_LINK);
		}

		user.changePassword(passwordHash);
		passwordPolicyService.remember(user);
		user.clearFailedLogin();
		// The link reached the mailbox, so the address is real (also true for the first password of an activation).
		if (user.getPerson() != null) {
			user.getPerson().markPrimaryEmailVerified();
		}
		userRepository.store(user);
		sessionService.deleteAllByUsername(user.getUsername());
	}

	// Returns true while the request is still allowed (PASSWORD_RESET_MAX_PER_HOUR per account per hour, default 3).
	private boolean withinRateLimit(String username) {
		String key = RATE_PREFIX + hash(username.toLowerCase(Locale.ROOT));
		Long count = redis.opsForValue().increment(key);
		if (count != null && count == 1L) {
			redis.expire(key, Duration.ofHours(1));
		}
		return count == null || count <= passwordPolicyService.resetMaxPerHour();
	}

	static String newToken() {
		byte[] bytes = new byte[32];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	static String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}

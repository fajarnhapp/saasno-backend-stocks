package com.saasno.service;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.NetworkUtility;
import com.saasno.utility.constant.Path;
import com.saasno.utility.RecoveryCodeUtility;
import com.saasno.utility.picker.UserStatus;
import com.saasno.utility.cryptography.Totp;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@Service
public class AuthenticationService {

	private static final int DEFAULT_MAX_LOGIN_ATTEMPTS = 5;
	private static final int DEFAULT_LOGIN_LOCK_MINUTES = 15;
	private static final String INVALID_CREDENTIALS = "Incorrect username or password";

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private PasswordPolicyService passwordPolicyService;

	@Autowired
	private MaintenanceModeService maintenanceModeService;

	@Autowired
	private TwoFactorPolicyService twoFactorPolicyService;

	@Autowired
	private DeviceTrackingService deviceTrackingService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private LoginHistoryService loginHistoryService;

	private void history(HttpServletRequest request, UserEntity user, String typed, boolean success, String reason) {
		loginHistoryService.record(user, typed, success, reason, clientIp(request), request.getHeader("User-Agent"));
	}

	// dontRollbackOn: the failed-attempt count must still be saved even though an ApiException is thrown.
	@Transactional(dontRollbackOn = ApiException.class)
	public ResponseModel<SessionEntity> authentication(HttpServletRequest request) {
		String username = request.getParameter("username");
		String password = request.getParameter("password");
		if (StringVerification.nullOrEmpty(username)) {
			throw new ApiException("400", "Username must not be empty");
		}

		if (StringVerification.nullOrEmpty(password)) {
			throw new ApiException("400", "Password must not be empty");
		}

		// Checked before the account is looked up, so the answer does not depend on whether the account exists.
		String allowlist = configurationRepository.get(ConfigurationKey.LOGIN_IP_ALLOWLIST);
		if (!NetworkUtility.allowed(clientIp(request), allowlist)) {
			history(request, null, username, false, "NETWORK");
			throw new ApiException("403", "Login is not allowed from your network");
		}

		// The username, or the primary email of the account, both work for signing in.
		UserEntity found = userRepository.findByLogin(username.trim());
		if (found == null) {
			history(request, null, username, false, "UNKNOWN_USER");
			// Same message as a wrong password so a valid username cannot be guessed.
			throw new ApiException("400", INVALID_CREDENTIALS);
		}

		// The row is locked so parallel guesses cannot slip past the count.
		UserEntity existingUser = userRepository.findLock(found.getId());
		if (existingUser.isLocked()) {
			history(request, existingUser, username, false, "LOCKED");
			throw lockedException(existingUser);
		}

		if (!existingUser.verifyPassword(password)) {
			history(request, existingUser, username, false, "WRONG_PASSWORD");
			throw failedAttempt(existingUser, INVALID_CREDENTIALS);
		}

		if (existingUser.getStatus() != UserStatus.ACTIVED) {
			history(request, existingUser, username, false, "INACTIVE");
			throw new ApiException("403", "Account is inactive, please contact the admin");
		}
		// During maintenance only the allowed roles can sign in. Checked after the password, so the answer says nothing about unknown accounts.
		maintenanceModeService.enforce(existingUser.getRoles().stream().map(role -> role.getId()).toList(), Path.API_LOGIN, "POST");
		existingUser.clearFailedLogin();

		Integer expiredSeconds = 68400;
		String rememberMe = request.getParameter("rememberMe");
		if (!StringVerification.nullOrEmpty(rememberMe) && rememberMe.equalsIgnoreCase("true")) {
			expiredSeconds = 615600;
		}

		SessionEntity session = new SessionEntity(existingUser, expiredSeconds);
		session.setIp(clientIp(request));
		String agent = request.getHeader("User-Agent");
		session.setUserAgent(agent == null ? null : agent.substring(0, Math.min(agent.length(), 255)));
		if (Boolean.TRUE.equals(existingUser.getTwoFactorAuth())) {
			session.setTwoFactorPending(true);
		}
		session.setPasswordChangeRequired(passwordPolicyService.isChangeRequired(existingUser));
		session.setTwoFactorSetupRequired(twoFactorPolicyService.isSetupRequired(existingUser));
		sessionService.create(session);
		if (!session.isTwoFactorPending()) {
			history(request, existingUser, username, true, "PASSWORD");
			deviceTrackingService.onLogin(existingUser, session);
		}
		return new ResponseModel<SessionEntity>().success(session);
	}

	@Transactional(dontRollbackOn = ApiException.class)
	public ResponseModel<SessionEntity> verifyOtp(HttpServletRequest request, String code) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			throw new ApiException("401", "Please log in first");
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length != 2 || !authorizationValue[0].equals("Bearer")) {
			throw new ApiException("401", "Invalid authorization format");
		}

		SessionEntity session = sessionService.read(authorizationValue[1]);
		if (session == null) {
			throw new ApiException("401", "Your session has expired, please log in again");
		}

		if (!session.isTwoFactorPending()) {
			return new ResponseModel<SessionEntity>().success(session);
		}

		UserEntity found = userRepository.findByUsername(session.getUsername());
		UserEntity user = found == null ? null : userRepository.findLock(found.getId());
		if (user == null) {
			throw new ApiException("400", "Invalid OTP code");
		}
		if (user.isLocked()) {
			sessionService.delete(authorizationValue[1]);
			throw lockedException(user);
		}
		// Either the 6-digit authenticator code, or one of the one-time recovery codes (XXXXX-XXXXX).
		boolean recovery = RecoveryCodeUtility.looksLikeRecoveryCode(code);
		boolean valid = recovery
				? user.consumeRecoveryCode(RecoveryCodeUtility.hash(code))
				: !StringVerification.nullOrEmpty(user.getTwoFactorAuthSecret()) && Totp.verify(user.getTwoFactorAuthSecret(), code);
		if (!valid) {
			history(request, user, user.getUsername(), false, recovery ? "WRONG_RECOVERY_CODE" : "WRONG_OTP");
			ApiException failure = failedAttempt(user, recovery ? "Invalid recovery code" : "Invalid OTP code");
			if (user.isLocked()) {
				// The pending session is discarded so the code cannot keep being guessed once the account is locked.
				sessionService.delete(authorizationValue[1]);
			}
			throw failure;
		}
		user.clearFailedLogin();

		session.setTwoFactorPending(false);
		sessionService.create(session);
		if (recovery) {
			int left = user.recoveryCodesLeft();
			notificationPublisher.notify(List.of(user.getId()), "RECOVERY_CODE_USED", "Recovery code used",
					"A recovery code was used to sign in to your account. " + left + " recovery code(s) left."
							+ (left == 0 ? " Set up new ones from Security." : ""), "/security");
		}
		history(request, user, user.getUsername(), true, recovery ? "RECOVERY_CODE" : "OTP");
		deviceTrackingService.onLogin(user, session);
		return new ResponseModel<SessionEntity>().success(session);
	}

	@Transactional
	public ResponseModel<SessionEntity> unauthenticated(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			return new ResponseModel<SessionEntity>().success();
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length == 2) {
			sessionService.delete(authorizationValue[1]);
		}

		return new ResponseModel<SessionEntity>().success();
	}

	// Origin IP: the X-Forwarded-For header from the frontend (when present), otherwise the connection address.
	private static String clientIp(HttpServletRequest request) {
		String forwarded = request.getHeader("X-Forwarded-For");
		String ip = forwarded != null && !forwarded.isBlank() ? forwarded.split(",")[0].trim() : request.getRemoteAddr();
		return ip == null ? null : ip.substring(0, Math.min(ip.length(), 64));
	}

	private ApiException failedAttempt(UserEntity user, String message) {
		int max = configInt(ConfigurationKey.MAX_LOGIN_ATTEMPTS, DEFAULT_MAX_LOGIN_ATTEMPTS);
		int minutes = configInt(ConfigurationKey.LOGIN_LOCK_MINUTES, DEFAULT_LOGIN_LOCK_MINUTES);
		boolean locked = user.recordFailedLogin(max, minutes);
		userRepository.update(user);
		if (locked) {
			return lockedException(user);
		}
		return new ApiException("400", message);
	}

	private ApiException lockedException(UserEntity user) {
		long minutes = Math.max(1, java.time.Duration.between(java.time.LocalDateTime.now(), user.getLockedUntil()).toMinutes() + 1);
		return new ApiException("429", "Too many failed attempts. Try again in " + minutes + " minute(s)");
	}

	private int configInt(ConfigurationKey key, int fallback) {
		String value = configurationRepository.get(key);
		try {
			int parsed = value == null ? fallback : Integer.parseInt(value.trim());
			return parsed < 1 ? fallback : parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}

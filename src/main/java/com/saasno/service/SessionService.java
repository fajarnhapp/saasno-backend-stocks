package com.saasno.service;

import java.io.IOException;
import java.util.Comparator;
import java.util.Set;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.AntPathMatcher;

import com.saasno.entity.ApiEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.repository.ApiRepository;
import com.saasno.repository.RoleRepository;
import com.saasno.utility.constant.Path;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.verification.StringVerification;

import jakarta.servlet.http.HttpServletRequest;

@Service
public class SessionService {

	@Autowired
	private ApiTokenService apiTokenService;

	@Autowired
	private RedisTemplate<String, Object> redisTemplate;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private ApiRepository apiRepository;

	@Autowired
	private MaintenanceModeService maintenanceModeService;

	private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

	static {
		PATH_MATCHER.setCaseSensitive(false);
	}

	private final String PREFIX = "session:";

	public static final String TWO_FACTOR_SETUP_REQUIRED = "Two-factor setup required";
	public static final String PASSWORD_CHANGE_REQUIRED = "Password change required";

	public void create(SessionEntity session) {
		long ttl = (session.getExpired().getTime() - System.currentTimeMillis()) / 1000;
		if (ttl > 0) {
			redisTemplate.opsForValue().set(
					PREFIX + session.getId(),
					session,
					ttl,
					TimeUnit.SECONDS);
		}
	}

	// All active sessions (keys session:*). There are few of them; used by the device list and forced sign-out.
	public java.util.List<SessionEntity> readAll() {
		java.util.List<SessionEntity> list = new java.util.ArrayList<>();
		Set<String> keys = redisTemplate.keys(PREFIX + "*");
		if (keys == null) {
			return list;
		}
		for (String key : keys) {
			Object value = redisTemplate.opsForValue().get(key);
			if (value instanceof SessionEntity session && session.getId() != null && key.equals(PREFIX + session.getId())) {
				list.add(session);
			}
		}
		return list;
	}

	// Public handle of a session: the session id is a token, so it is never sent to the client; only its hash is.
	public static String handle(SessionEntity session) {
		try {
			byte[] digest = java.security.MessageDigest.getInstance("SHA-256").digest(session.getId().getBytes(java.nio.charset.StandardCharsets.UTF_8));
			StringBuilder out = new StringBuilder();
			for (int i = 0; i < 12; i++) {
				out.append(String.format("%02x", digest[i]));
			}
			return out.toString();
		} catch (java.security.NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

	public SessionEntity read(String sessionId) {
		return (SessionEntity) redisTemplate.opsForValue().get(PREFIX + sessionId);
	}

	public void delete(String sessionId) {
		redisTemplate.delete(PREFIX + sessionId);
	}

	// Signs the user out everywhere. Matches on the stored username instead of a key pattern, so special characters in a username
	// cannot widen or break the match.
	public void deleteAllByUsername(String username) {
		for (SessionEntity session : readAll()) {
			if (username != null && username.equals(session.getUsername())) {
				delete(session.getId());
			}
		}
	}

	public ResponseModel<SessionEntity> check(HttpServletRequest request, String path, String method)
			throws IOException {
		ResponseModel<SessionEntity> session = check(request);

		if (session.getData().isApiToken() && !allowedWithApiToken(path)) {
			throw new ApiException("403", "This is not available with an API token");
		}

		if (session.getData().isTwoFactorPending()) {
			boolean isOtpVerifyEndpoint = path.equals(Path.API_LOGIN_OTP_VERIFY) && method.equalsIgnoreCase("POST");
			if (isOtpVerifyEndpoint) {
				return session;
			}
			throw new ApiException("401", "OTP verification required");
		}

		if (session.getData().isPasswordChangeRequired() && !isAllowedWhilePasswordChangeRequired(path, method)) {
			throw new ApiException("403", PASSWORD_CHANGE_REQUIRED);
		}

		// The password comes first: while it is also pending, the password rule above is the only restriction.
		if (session.getData().isTwoFactorSetupRequired() && !session.getData().isPasswordChangeRequired()
				&& !isAllowedWhileTwoFactorSetupRequired(path, method)) {
			throw new ApiException("403", TWO_FACTOR_SETUP_REQUIRED);
		}

		maintenanceModeService.enforce(session.getData().getRoles(), path, method);

		if (session.getData().getRoles() == null || session.getData().getRoles().isEmpty()) {
			throw new ApiException("404", "User role is unknown");
		}

		ApiEntity target = findBestMatchingApi(path, method);
		if (target != null) {
			for (String roleId : session.getData().getRoles()) {
				RoleEntity role = roleRepository.find(roleId);

				if (role != null && role.getApis() != null) {
					boolean hasMatch = role.getApis().stream().anyMatch(api -> api.getId().equals(target.getId()));

					if (hasMatch) {
						return session;
					}
				}
			}
		}

		throw new ApiException("403", "Your account does not have access to this feature");
	}

	// A token cannot create more tokens, change the password or touch two-factor settings, so a leaked token cannot make itself permanent.
	static boolean allowedWithApiToken(String path) {
		return !(path.equals(Path.API_TOKEN) || path.startsWith(Path.API_TOKEN + "/") || path.startsWith(Path.API_USER + "/change-password")
				|| path.startsWith(Path.API_USER + "/verify-password") || path.startsWith(Path.API_USER_OTP) || path.startsWith(Path.API_PERSON + "/me/verify-email") || path.equals(Path.API_PERSON + "/me/export")
				|| path.equals(Path.API_PRIVACY + "/delete-request"));
	}

	// Until the password is changed, a session may only read its own profile, change the password, and log out.
	private static boolean isAllowedWhilePasswordChangeRequired(String path, String method) {
		return (path.equals(Path.API_USER + "/change-password") && method.equalsIgnoreCase("PUT"))
				|| (path.equals(Path.API_USER + "/me") && method.equalsIgnoreCase("GET"))
				|| (path.equals(Path.API_LOGOUT) && method.equalsIgnoreCase("DELETE"));
	}

	// Until two-factor authentication is set up, a session may only read its own profile, run the setup, and log out.
	private static boolean isAllowedWhileTwoFactorSetupRequired(String path, String method) {
		return (path.equals(Path.API_USER_OTP + "/setup") && method.equalsIgnoreCase("POST"))
				|| (path.equals(Path.API_USER_OTP + "/enable") && method.equalsIgnoreCase("POST"))
				|| (path.equals(Path.API_USER + "/me") && method.equalsIgnoreCase("GET"))
				|| (path.equals(Path.API_LOGOUT) && method.equalsIgnoreCase("DELETE"));
	}

	/**
	 * Find the most specific registered API matching the request, so a pattern like
	 * /api/{id}/product never grants access to a more specific static path such as
	 * /api/warehouse/product.
	 */
	private ApiEntity findBestMatchingApi(String path, String method) {
		Comparator<String> comparator = PATH_MATCHER.getPatternComparator(path);
		return apiRepository.findByMethod(method).stream()
				.filter(api -> PATH_MATCHER.match(api.getPath(), path))
				.min((a, b) -> comparator.compare(a.getPath(), b.getPath()))
				.orElse(null);
	}

	public ResponseModel<SessionEntity> check(HttpServletRequest request) {
		String authorization = request.getHeader("Authorization");
		if (StringVerification.nullOrEmpty(authorization)) {
			throw new ApiException("401", "Please log in first");
		}

		String[] authorizationValue = authorization.split(" ");
		if (authorizationValue.length != 2) {
			throw new ApiException("401", "Invalid authorization format");
		}

		if (!authorizationValue[0].equals("Bearer")) {
			throw new ApiException("401", "Invalid authorization type");
		}

		if (ApiTokenService.looksLikeToken(authorizationValue[1])) {
			return new ResponseModel<SessionEntity>().success(apiTokenService.authenticate(authorizationValue[1]));
		}

		SessionEntity session = read(authorizationValue[1]);
		if (session == null) {
			throw new ApiException("401", "Your session has expired, please log in again");
		}

		return new ResponseModel<SessionEntity>().success(session);
	}
}

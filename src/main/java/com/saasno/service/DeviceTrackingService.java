package com.saasno.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.BrandColor;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

// Tells the user when their account is used from a browser/device it has not been used from before. A device is identified by its
// User-Agent, remembered per user in Redis for a year. The very first login is not reported (there is nothing to compare with), and
// if Redis is emptied the next login of each user is simply treated as first again.
@Service
public class DeviceTrackingService {

	private static final String PREFIX = "devices:";
	private static final Duration REMEMBER = Duration.ofDays(365);
	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm");

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private MailService mailService;

	@Autowired
	private AppSettingService appSettingService;

	@Autowired
	private EmailTemplateService emailTemplateService;

	@Autowired
	private ConfigurationRepository configurationRepository;

	// Called once a login is complete (after two-step verification when it is on). Never makes the login fail.
	public void onLogin(UserEntity user, SessionEntity session) {
		try {
			String key = PREFIX + user.getId();
			String device = hash(session.getUserAgent() == null ? "unknown" : session.getUserAgent());
			boolean hadDevices = Boolean.TRUE.equals(redis.hasKey(key));
			Long added = redis.opsForSet().add(key, device);
			redis.expire(key, REMEMBER);
			if (hadDevices && added != null && added > 0) {
				notifyNewDevice(user, session);
			}
		} catch (Exception e) {
			Log.error("Device tracking failed: " + e.getMessage());
		}
	}

	private void notifyNewDevice(UserEntity user, SessionEntity session) {
		String when = LocalDateTime.now().format(TIME);
		String agent = shorten(session.getUserAgent());
		String ip = session.getIp() == null ? "unknown" : session.getIp();
		notificationPublisher.notify(List.of(user.getId()), "NEW_DEVICE_LOGIN", "New sign-in",
				"Your account was used from a new device (" + agent + ", IP " + ip + "). If this was not you, change your password.",
				"/profile");

		String frontendUrl = configurationRepository.get(ConfigurationKey.FRONTEND_URL);
		String email = user.getPerson() == null ? null : user.getPerson().primaryEmail();
		if (email != null && mailService.configured()) {
			String appName = appSettingService.appName();
			String link = frontendUrl == null || frontendUrl.isBlank() ? null : frontendUrl.trim().replaceAll("/+$", "") + "/profile";
			EmailTemplateService.Text text = emailTemplateService.resolve(EmailTemplateService.NEW_DEVICE_LOGIN,
					Map.of("appName", appName, "username", user.getUsername()));
			mailService.sendAsync(email, EmailTemplate.build(appName, BrandColor.hex(appSettingService.hue(), appSettingService.chroma()),
					text.subject(), text.heading(), text.intro(),
					List.of(new EmailTemplate.Detail("Time", when), new EmailTemplate.Detail("IP address", ip),
							new EmailTemplate.Detail("Device", agent)),
					text.buttonLabel(), link, text.note()), appName);
		}
	}

	private static String shorten(String agent) {
		if (agent == null || agent.isBlank()) {
			return "unknown device";
		}
		String value = agent.strip();
		return value.length() <= 120 ? value : value.substring(0, 119) + "…";
	}

	static String hash(String value) {
		try {
			return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}

}

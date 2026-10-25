package com.saasno.service;

import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.saasno.entity.ContactEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.PersonRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.BrandColor;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.picker.ContactType;

import jakarta.transaction.Transactional;

// Confirms that an email address really belongs to the person: a one-time link is emailed to it, and opening the link marks it verified.
// Like the password reset link, only the SHA-256 of the token is kept (in Redis, with an expiry).
@Service
public class EmailVerificationService {

	static final String INVALID_LINK = "The verification link is invalid or has expired";

	private static final String TOKEN_PREFIX = "emailverify:token:";
	private static final String RATE_PREFIX = "emailverify:rate:";
	private static final Duration TTL = Duration.ofHours(24);
	private static final int MAX_PER_HOUR = 5;

	@Autowired
	private StringRedisTemplate redis;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private PersonRepository personRepository;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private MailService mailService;

	@Autowired
	private AppSettingService appSettingService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private EmailTemplateService emailTemplateService;

	// Sends the link for one of the signed-in user's own email addresses.
	@Transactional
	public void request(String contactId) {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		ContactEntity contact = user == null || user.getPerson() == null ? null
				: user.getPerson().getContacts().stream().filter(c -> c.getId().equals(contactId)).findFirst().orElse(null);
		if (contact == null || contact.getType() != ContactType.EMAIL) {
			throw new ApiException("404", "Email address does not exist");
		}
		if (contact.getVerifiedAt() != null) {
			throw new ApiException("409", "This email address is already verified");
		}
		String frontendUrl = configurationRepository.get(ConfigurationKey.FRONTEND_URL);
		if (!mailService.configured() || frontendUrl == null || frontendUrl.isBlank()) {
			throw new ApiException("409", "Email is not set up on this server, ask an administrator");
		}
		String rateKey = RATE_PREFIX + contact.getId();
		Long count = redis.opsForValue().increment(rateKey);
		if (count != null && count == 1L) {
			redis.expire(rateKey, Duration.ofHours(1));
		}
		if (count != null && count > MAX_PER_HOUR) {
			throw new ApiException("429", "Too many requests, please try again later");
		}

		String token = PasswordResetService.newToken();
		// The address is stored with the token: when the address changes before the link is opened, the link no longer works.
		redis.opsForValue().set(TOKEN_PREFIX + PasswordResetService.hash(token), contact.getId() + "|" + contact.getValue().toLowerCase(Locale.ROOT), TTL);

		String link = frontendUrl.trim().replaceAll("/+$", "") + "/verify-email?token=" + token;
		String appName = appSettingService.appName();
		EmailTemplateService.Text text = emailTemplateService.resolve(EmailTemplateService.EMAIL_VERIFICATION,
				Map.of("appName", appName, "lifetime", TTL.toHours() + " hours"));
		mailService.sendAsync(contact.getValue(), EmailTemplate.build(appName, BrandColor.hex(appSettingService.hue(), appSettingService.chroma()),
				text.subject(), text.heading(), text.intro(), List.of(), text.buttonLabel(), link, text.note()), appName);
	}

	@Transactional
	public void confirm(String token) {
		if (token == null || token.length() < 20 || token.length() > 128) {
			throw new ApiException("400", INVALID_LINK);
		}
		String stored = redis.opsForValue().getAndDelete(TOKEN_PREFIX + PasswordResetService.hash(token));
		if (stored == null || !stored.contains("|")) {
			throw new ApiException("400", INVALID_LINK);
		}
		String[] parts = stored.split("\\|", 2);
		ContactEntity contact = personRepository.getEntityManager().find(ContactEntity.class, parts[0]);
		if (contact == null || contact.getType() != ContactType.EMAIL || !contact.getValue().toLowerCase(Locale.ROOT).equals(parts[1])) {
			throw new ApiException("400", INVALID_LINK);
		}
		contact.markVerified();
	}
}

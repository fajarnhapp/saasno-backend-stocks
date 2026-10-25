package com.saasno.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.ContactEntity;
import com.saasno.entity.DeletionRequestEntity;
import com.saasno.entity.PrivacyConsentEntity;
import com.saasno.entity.PrivacyPolicyEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.ApiTokenRepository;
import com.saasno.repository.LoginHistoryRepository;
import com.saasno.repository.PrivacyRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.privacy.PrivacyDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.transaction.Transactional;

// The privacy policy and what comes with it: who has read which version, a copy of one's own data, and requests to delete an account.
@Service
public class PrivacyService {

	static final int MAX_CONTENT = 50_000;
	static final int EXPORT_HISTORY = 1000;
	static final String ADMIN_ROLE = "SUPER";

	@Autowired
	private PrivacyRepository privacyRepository;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private LoginHistoryRepository loginHistoryRepository;

	@Autowired
	private ApiTokenRepository apiTokenRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private UserService userService;

	@Autowired
	private NotificationPublisher notificationPublisher;

	@Autowired
	private ChangeLogService changeLogService;

	// ---- the text ----

	// The policy shown to everyone: the latest published version, or the built-in text. In the asked language when it exists, else in the other one.
	@Transactional
	public PrivacyDTO.Policy current(String language) {
		PrivacyPolicyEntity latest = privacyRepository.latestPolicy();
		boolean indonesian = "id".equals(language);
		if (latest == null) {
			return new PrivacyDTO.Policy(0, PrivacyDefaults.text(indonesian ? "id" : "en"), null, null, true, true);
		}
		String english = latest.getContent();
		String local = latest.getContentId();
		String wanted = indonesian ? local : english;
		String text = blank(wanted) ? (indonesian ? english : local) : wanted;
		return new PrivacyDTO.Policy(latest.getVersion(), text, latest.getPublishedAt(), latest.getPublishedBy(), false,
				!blank(english) && !blank(local));
	}

	@Transactional
	public ResponseModel<PrivacyDTO.Admin> admin() {
		List<PrivacyDTO.Version> versions = privacyRepository.policies().stream()
				.map(p -> new PrivacyDTO.Version(p.getVersion(), p.getPublishedAt(), p.getPublishedBy())).toList();
		PrivacyPolicyEntity latest = privacyRepository.latestPolicy();
		PrivacyDTO.Admin admin = latest == null
				? new PrivacyDTO.Admin(0, true, PrivacyDefaults.text("en"), PrivacyDefaults.text("id"), versions)
				: new PrivacyDTO.Admin(latest.getVersion(), false, latest.getContent() == null ? "" : latest.getContent(),
						latest.getContentId() == null ? "" : latest.getContentId(), versions);
		return new ResponseModel<PrivacyDTO.Admin>().success(admin);
	}

	// A new version replaces the text for everybody, and everybody is asked to read it again.
	@Transactional
	public ResponseModel<PrivacyDTO.Policy> publish(PrivacyDTO.Publish request) {
		String english = clean(request == null ? null : request.content());
		String indonesian = clean(request == null ? null : request.contentId());
		if (english.isEmpty() && indonesian.isEmpty()) {
			throw new ApiException("400", "The policy must not be empty");
		}
		if (english.length() > MAX_CONTENT || indonesian.length() > MAX_CONTENT) {
			throw new ApiException("400", "Each text must be at most " + MAX_CONTENT + " characters");
		}
		PrivacyPolicyEntity latest = privacyRepository.latestPolicy();
		if (latest != null && english.equals(clean(latest.getContent())) && indonesian.equals(clean(latest.getContentId()))) {
			throw new ApiException("409", "This is the same text as the current version");
		}
		int version = latest == null ? 1 : latest.getVersion() + 1;
		privacyRepository.store(new PrivacyPolicyEntity(version, english.isEmpty() ? null : english, indonesian.isEmpty() ? null : indonesian,
				sessionUtility.getUsername()));
		changeLogService.event("PRIVACY_POLICY", String.valueOf(version), "Privacy policy version " + version, "published");
		return new ResponseModel<PrivacyDTO.Policy>().success(current("en"));
	}

	private static String clean(String text) {
		return text == null ? "" : text.replace("\r\n", "\n").trim();
	}

	private static boolean blank(String text) {
		return text == null || text.isBlank();
	}

	// ---- the signed-in user ----

	@Transactional
	public ResponseModel<PrivacyDTO.Status> status() {
		UserEntity user = currentUser();
		PrivacyDTO.Policy policy = current("en");
		PrivacyConsentEntity accepted = privacyRepository.consent(user.getId(), policy.version());
		List<PrivacyConsentEntity> all = privacyRepository.consents(user.getId());
		PrivacyConsentEntity latestAccepted = all.isEmpty() ? null : all.get(0);
		// Nobody is asked while the policy is still the built-in text with blanks to fill in.
		boolean needs = !policy.builtIn() && accepted == null;
		return new ResponseModel<PrivacyDTO.Status>().success(new PrivacyDTO.Status(policy.version(), policy.builtIn(),
				latestAccepted == null ? null : latestAccepted.getVersion(), latestAccepted == null ? null : latestAccepted.getAcceptedAt(), needs,
				privacyRepository.pendingRequest(user.getId()) != null));
	}

	@Transactional
	public ResponseModel<Void> accept() {
		UserEntity user = currentUser();
		int version = current("en").version();
		if (privacyRepository.consent(user.getId(), version) == null) {
			privacyRepository.store(new PrivacyConsentEntity(user.getId(), version));
		}
		return new ResponseModel<Void>().success();
	}

	// A copy of everything the application keeps about the signed-in user, for them to keep (JSON).
	@Transactional
	public ResponseModel<Map<String, Object>> exportMyData() {
		UserEntity user = currentUser();
		Map<String, Object> data = new LinkedHashMap<>();
		data.put("exportedAt", java.time.LocalDateTime.now().toString());

		Map<String, Object> account = new LinkedHashMap<>();
		account.put("username", user.getUsername());
		account.put("status", user.getStatus());
		account.put("roles", user.roleNames());
		account.put("twoFactorAuthEnabled", user.getTwoFactorAuth());
		account.put("registered", user.getRegistered());
		account.put("modificated", user.getModificated());
		data.put("account", account);

		Map<String, Object> person = new LinkedHashMap<>();
		person.put("fullName", user.getPerson().getFullName());
		person.put("nickname", user.getPerson().getNickname());
		person.put("birthPlace", user.getPerson().getBirthPlace());
		person.put("birthDate", user.getPerson().getBirthDate());
		person.put("gender", user.getPerson().getGender());
		person.put("religion", user.getPerson().getReligion());
		data.put("person", person);

		data.put("contacts", user.getPerson().getContacts().stream().map(this::describe).toList());
		data.put("signInHistory", loginHistoryRepository.latest(user.getId(), EXPORT_HISTORY).stream().map(h -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("time", h.getRegistered());
			row.put("success", h.isSuccess());
			row.put("reason", h.getReason());
			row.put("ip", h.getIp());
			row.put("device", h.getUserAgent());
			return row;
		}).toList());
		data.put("privacyPolicyReadings", privacyRepository.consents(user.getId()).stream().map(c -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("version", c.getVersion());
			row.put("acceptedAt", c.getAcceptedAt());
			return row;
		}).toList());
		data.put("apiTokens", apiTokenRepository.usable(user.getId(), java.time.LocalDateTime.now()).stream().map(t -> {
			Map<String, Object> row = new LinkedHashMap<>();
			row.put("name", t.getName());
			row.put("prefix", t.getPrefix());
			row.put("expiresAt", t.getExpiresAt());
			row.put("lastUsedAt", t.getLastUsedAt());
			return row;
		}).toList());
		changeLogService.event("USER", user.getId(), user.getUsername(), "personal data downloaded");
		return new ResponseModel<Map<String, Object>>().success(data);
	}

	private Map<String, Object> describe(ContactEntity contact) {
		Map<String, Object> row = new LinkedHashMap<>();
		row.put("type", contact.getType());
		row.put("value", contact.getValue());
		row.put("label", contact.getLabel());
		row.put("primary", contact.isPrimary());
		row.put("verifiedAt", contact.getVerifiedAt());
		return row;
	}

	// ---- deleting an account ----

	@Transactional
	public ResponseModel<Void> requestDeletion() {
		UserEntity user = currentUser();
		if (privacyRepository.pendingRequest(user.getId()) != null) {
			throw new ApiException("409", "You already asked for your account to be deleted; an administrator will handle it");
		}
		privacyRepository.store(new DeletionRequestEntity(user.getId(), user.getUsername()));
		changeLogService.event("USER", user.getId(), user.getUsername(), "asked for the account to be deleted");
		List<String> admins = privacyRepository.userIdsWithRole(ADMIN_ROLE);
		if (!admins.isEmpty()) {
			notificationPublisher.notify(admins, "DELETION_REQUEST", "Account deletion requested",
					user.getUsername() + " asked for their account to be deleted.", "/system/privacy");
		}
		return new ResponseModel<Void>().success();
	}

	@Transactional
	public ResponseModel<List<PrivacyDTO.DeletionRequest>> requests() {
		return new ResponseModel<List<PrivacyDTO.DeletionRequest>>().success(privacyRepository.requests().stream()
				.map(r -> new PrivacyDTO.DeletionRequest(r.getId(), r.getUsername(), r.getStatus(), r.getRequestedAt(), r.getHandledAt(), r.getHandledBy())).toList());
	}

	// APPROVE moves the user to the trash; REJECT leaves the account as it is.
	@Transactional
	public ResponseModel<Void> handle(String id, String action) {
		DeletionRequestEntity request = privacyRepository.request(id);
		if (request == null || !DeletionRequestEntity.PENDING.equals(request.getStatus())) {
			throw new ApiException("404", "There is no pending request");
		}
		String choice = action == null ? "" : action.trim().toUpperCase(java.util.Locale.ROOT);
		if (choice.equals("APPROVE")) {
			userService.delete(request.getUserId());
			request.handle(DeletionRequestEntity.DONE, sessionUtility.getUsername());
		} else if (choice.equals("REJECT")) {
			request.handle(DeletionRequestEntity.REJECTED, sessionUtility.getUsername());
			notificationPublisher.notify(List.of(request.getUserId()), "DELETION_REJECTED", "Account deletion",
					"Your request to delete the account was not approved. Contact an administrator for details.", "/profile");
		} else {
			throw new ApiException("400", "Action must be APPROVE or REJECT");
		}
		return new ResponseModel<Void>().success();
	}

	private UserEntity currentUser() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		return user;
	}
}

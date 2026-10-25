package com.saasno.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.EmailTemplateEntity;
import com.saasno.repository.EmailTemplateRepository;
import com.saasno.utility.dto.emailtemplate.EmailTemplateDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import jakarta.transaction.Transactional;

// The wording of the emails the app sends. Each has a built-in text (below); an admin can change it on the Email Template page, and
// {{placeholders}} are filled in when the email is sent. The layout (colours, button) stays the same for all emails.
@Service
public class EmailTemplateService {

	static final String PASSWORD_RESET = "PASSWORD_RESET";
	static final String ACCOUNT_ACTIVATION = "ACCOUNT_ACTIVATION";
	static final String EMAIL_VERIFICATION = "EMAIL_VERIFICATION";
	static final String NEW_DEVICE_LOGIN = "NEW_DEVICE_LOGIN";

	private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Za-z]+)\\s*}}");

	// The text of one email: subject, heading, intro, button label, closing note.
	record Text(String subject, String heading, String intro, String buttonLabel, String note) {
	}

	private record Definition(String name, String description, List<String> placeholders, Text defaults) {
	}

	private static final Map<String, Definition> DEFINITIONS = new LinkedHashMap<>();
	static {
		DEFINITIONS.put(PASSWORD_RESET, new Definition("Password reset", "Sent when someone asks to reset a forgotten password.",
				List.of("appName", "username", "lifetime"),
				new Text("Reset your {{appName}} password", "Reset your password",
						"We received a request to reset the password of your account. Use the button below to choose a new one.",
						"Reset password",
						"This link works once and expires in {{lifetime}}. If you did not ask for this, ignore this email: your password stays the same. "
								+ "Never share this link with anyone.")));
		DEFINITIONS.put(ACCOUNT_ACTIVATION, new Definition("Account activation",
				"Sent to a new user, or to a user whose password an admin reset, to choose their own password.",
				List.of("appName", "username", "lifetime"),
				new Text("Activate your {{appName}} account", "Welcome to {{appName}}",
						"An account was created for you (username: {{username}}). Use the button below to choose your password and sign in.",
						"Choose my password", "This link works once and expires in {{lifetime}}. Never share it with anyone.")));
		DEFINITIONS.put(EMAIL_VERIFICATION, new Definition("Email confirmation", "Sent to confirm that an email address belongs to the person.",
				List.of("appName", "lifetime"),
				new Text("Confirm your email address for {{appName}}", "Confirm your email address",
						"Someone added this email address to an account on {{appName}}. Use the button below to confirm that it is yours.",
						"Confirm email address",
						"This link works once and expires in {{lifetime}}. If you do not know {{appName}}, ignore this email.")));
		DEFINITIONS.put(NEW_DEVICE_LOGIN, new Definition("New sign-in alert", "Sent when the account is used from a device that was not seen before.",
				List.of("appName", "username"),
				new Text("New sign-in to your {{appName}} account", "New sign-in to your account",
						"Your account was just used to sign in from a device we have not seen before.", "Review your sessions",
						"If this was you, there is nothing to do. If it was not, change your password now and sign out the other devices from your profile.")));
	}

	@Autowired
	private EmailTemplateRepository emailTemplateRepository;

	@Autowired
	private ChangeLogService changeLogService;

	// The text to send: the customized one when it exists, else the built-in one, with the placeholders filled in.
	Text resolve(String id, Map<String, String> values) {
		Definition definition = definition(id);
		EmailTemplateEntity custom = emailTemplateRepository.find(id);
		Text text = custom == null ? definition.defaults()
				: new Text(custom.getSubject(), custom.getHeading(), custom.getIntro(), custom.getButtonLabel(), custom.getNote());
		return new Text(oneLine(fill(text.subject(), values)), fill(text.heading(), values), fill(text.intro(), values),
				fill(text.buttonLabel(), values), fill(text.note(), values));
	}

	@Transactional
	public ResponseModel<List<EmailTemplateDTO>> list() {
		List<EmailTemplateDTO> list = DEFINITIONS.keySet().stream().map(this::describe).toList();
		return new ResponseModel<List<EmailTemplateDTO>>().success(list);
	}

	@Transactional
	public ResponseModel<EmailTemplateDTO> update(EmailTemplateDTO dto) {
		Definition definition = definition(dto.getId());
		Text text = new Text(required(dto.getSubject(), "Subject", 200), required(dto.getHeading(), "Heading", 200),
				required(dto.getIntro(), "Message", 1000), required(dto.getButtonLabel(), "Button", 60), optional(dto.getNote(), "Note", 1000));
		for (String field : List.of(text.subject(), text.heading(), text.intro(), text.buttonLabel(), text.note())) {
			Matcher matcher = PLACEHOLDER.matcher(field);
			while (matcher.find()) {
				if (!definition.placeholders().contains(matcher.group(1))) {
					throw new ApiException("400", "Unknown placeholder {{" + matcher.group(1) + "}}; use only " + placeholderList(definition));
				}
			}
		}

		EmailTemplateEntity entity = emailTemplateRepository.find(dto.getId());
		boolean created = entity == null;
		Text before = created ? definition.defaults()
				: new Text(entity.getSubject(), entity.getHeading(), entity.getIntro(), entity.getButtonLabel(), entity.getNote());
		if (created) {
			entity = new EmailTemplateEntity(dto.getId());
		}
		entity.setSubject(text.subject());
		entity.setHeading(text.heading());
		entity.setIntro(text.intro());
		entity.setButtonLabel(text.buttonLabel());
		entity.setNote(text.note());
		emailTemplateRepository.store(entity);

		changeLogService.updated("EMAIL_TEMPLATE", dto.getId(), definition.name(), ChangeLogService.changes()
				.add("subject", before.subject(), text.subject()).add("heading", before.heading(), text.heading())
				.add("message", before.intro(), text.intro()).add("button", before.buttonLabel(), text.buttonLabel())
				.add("note", before.note(), text.note()));
		return new ResponseModel<EmailTemplateDTO>().success(describe(dto.getId()));
	}

	// Back to the built-in text.
	@Transactional
	public ResponseModel<EmailTemplateDTO> reset(String id) {
		Definition definition = definition(id);
		EmailTemplateEntity entity = emailTemplateRepository.find(id);
		if (entity != null) {
			emailTemplateRepository.delete(entity);
			changeLogService.event("EMAIL_TEMPLATE", id, definition.name(), "reset to the built-in text");
		}
		return new ResponseModel<EmailTemplateDTO>().success(describe(id));
	}

	private EmailTemplateDTO describe(String id) {
		Definition definition = definition(id);
		EmailTemplateEntity custom = emailTemplateRepository.find(id);
		EmailTemplateDTO dto = fromText(id, custom == null ? definition.defaults()
				: new Text(custom.getSubject(), custom.getHeading(), custom.getIntro(), custom.getButtonLabel(), custom.getNote()));
		dto.setName(definition.name());
		dto.setDescription(definition.description());
		dto.setPlaceholders(definition.placeholders());
		dto.setCustomized(custom != null);
		dto.setDefaults(fromText(id, definition.defaults()));
		return dto;
	}

	private static EmailTemplateDTO fromText(String id, Text text) {
		EmailTemplateDTO dto = new EmailTemplateDTO();
		dto.setId(id);
		dto.setSubject(text.subject());
		dto.setHeading(text.heading());
		dto.setIntro(text.intro());
		dto.setButtonLabel(text.buttonLabel());
		dto.setNote(text.note());
		return dto;
	}

	private static Definition definition(String id) {
		Definition definition = id == null ? null : DEFINITIONS.get(id);
		if (definition == null) {
			throw new ApiException("404", "Email template does not exist");
		}
		return definition;
	}

	// Replaces {{name}} by its value; a name without a value stays visible, so a mistake shows up instead of vanishing.
	static String fill(String template, Map<String, String> values) {
		Matcher matcher = PLACEHOLDER.matcher(template);
		StringBuilder out = new StringBuilder();
		while (matcher.find()) {
			String value = values.get(matcher.group(1));
			matcher.appendReplacement(out, Matcher.quoteReplacement(value == null ? matcher.group() : value));
		}
		matcher.appendTail(out);
		return out.toString();
	}

	private static String placeholderList(Definition definition) {
		return definition.placeholders().stream().map(p -> "{{" + p + "}}").reduce((a, b) -> a + ", " + b).orElse("none");
	}

	private static String oneLine(String value) {
		return value.replaceAll("[\\r\\n]+", " ").trim();
	}

	private static String required(String value, String name, int max) {
		String text = value == null ? "" : value.trim();
		if (text.isEmpty()) {
			throw new ApiException("400", name + " must not be empty");
		}
		return limit(text, name, max);
	}

	private static String optional(String value, String name, int max) {
		return limit(value == null ? "" : value.trim(), name, max);
	}

	private static String limit(String text, String name, int max) {
		if (text.length() > max) {
			throw new ApiException("400", name + " must be at most " + max + " characters");
		}
		return text;
	}
}

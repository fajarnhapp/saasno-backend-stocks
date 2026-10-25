package com.saasno.service;

import java.io.UnsupportedEncodingException;
import java.util.Properties;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

// Sends email through SMTP. Everything about the mail server (MAIL_HOST, MAIL_PORT, MAIL_USERNAME, MAIL_PASSWORD, MAIL_AUTH,
// MAIL_STARTTLS) and the sender (MAIL_FROM, MAIL_FROM_NAME) is kept in the configuration table, so it can be changed on the
// Configuration page without a restart; it is read at every send. While MAIL_HOST is empty every call is a no-op (callers can ask
// configured()). Only the connection timeout stays in application.properties.
@Service
public class MailService {

	static final int DEFAULT_PORT = 587;
	static final String DEFAULT_FROM = "no-reply@saasno.local";
	static final String DEFAULT_FROM_NAME = "Saasno";

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Value("${app.mail.timeout-ms:5000}")
	private int timeoutMs;

	public boolean configured() {
		return !blank(configurationRepository.get(ConfigurationKey.MAIL_HOST));
	}

	// Sent off the calling thread so the response time of the request does not depend on (or reveal anything about) the mail server.
	void sendAsync(String to, EmailTemplate.Mail mail, String senderName) {
		JavaMailSender sender = sender();
		if (sender == null) {
			return;
		}
		MimeMessage message = sender.createMimeMessage();
		try {
			MimeMessageHelper helper = new MimeMessageHelper(message, MimeMessageHelper.MULTIPART_MODE_MIXED_RELATED, "UTF-8");
			helper.setFrom(address(configurationRepository.get(ConfigurationKey.MAIL_FROM)),
					displayName(configurationRepository.get(ConfigurationKey.MAIL_FROM_NAME), senderName));
			helper.setTo(to);
			helper.setSubject(mail.subject());
			helper.setText(mail.text(), mail.html());
		} catch (MessagingException | UnsupportedEncodingException e) {
			Log.error("Failed to build the email: " + e.getMessage());
			return;
		}
		Thread.startVirtualThread(() -> {
			try {
				sender.send(message);
			} catch (Exception e) {
				Log.error("Failed to send the email: " + e.getMessage());
			}
		});
	}

	// null while the mail server is not configured.
	private JavaMailSender sender() {
		String host = configurationRepository.get(ConfigurationKey.MAIL_HOST);
		if (blank(host)) {
			return null;
		}
		return build(host.trim(), port(configurationRepository.get(ConfigurationKey.MAIL_PORT)),
				configurationRepository.get(ConfigurationKey.MAIL_USERNAME), configurationRepository.get(ConfigurationKey.MAIL_PASSWORD),
				flag(configurationRepository.get(ConfigurationKey.MAIL_AUTH)), flag(configurationRepository.get(ConfigurationKey.MAIL_STARTTLS)),
				timeoutMs);
	}

	// Login is attempted only when there is a user name; a server without login just needs the host and port.
	static JavaMailSenderImpl build(String host, int port, String username, String password, boolean auth, boolean starttls, int timeoutMs) {
		boolean login = auth && !blank(username);
		JavaMailSenderImpl sender = new JavaMailSenderImpl();
		sender.setHost(host);
		sender.setPort(port);
		sender.setDefaultEncoding("UTF-8");
		if (login) {
			sender.setUsername(username.trim());
			sender.setPassword(password == null ? "" : password);
		}
		Properties properties = sender.getJavaMailProperties();
		properties.put("mail.smtp.auth", String.valueOf(login));
		properties.put("mail.smtp.starttls.enable", String.valueOf(starttls));
		properties.put("mail.smtp.connectiontimeout", String.valueOf(timeoutMs));
		properties.put("mail.smtp.timeout", String.valueOf(timeoutMs));
		properties.put("mail.smtp.writetimeout", String.valueOf(timeoutMs));
		return sender;
	}

	static int port(String value) {
		try {
			int port = Integer.parseInt(value == null ? "" : value.trim());
			return port >= 1 && port <= 65535 ? port : DEFAULT_PORT;
		} catch (NumberFormatException e) {
			return DEFAULT_PORT;
		}
	}

	// The sender address; the default is used while MAIL_FROM is missing.
	static String address(String configured) {
		return blank(configured) ? DEFAULT_FROM : configured.trim();
	}

	// MAIL_FROM_NAME wins; otherwise the name the caller gives (the app name); otherwise "Saasno".
	static String displayName(String configured, String callerName) {
		if (!blank(configured)) {
			return configured.trim();
		}
		return blank(callerName) ? DEFAULT_FROM_NAME : callerName.trim();
	}

	// "true" unless the setting is exactly "false" (also while it is missing).
	static boolean flag(String value) {
		return !"false".equalsIgnoreCase(value == null ? "" : value.trim());
	}

	private static boolean blank(String value) {
		return value == null || value.isBlank();
	}

}

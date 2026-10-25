package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;

class MailServiceTest {

	private ConfigurationRepository configuration;
	private MailService service;

	@BeforeEach
	void setUp() {
		configuration = mock(ConfigurationRepository.class);
		service = new MailService();
		ReflectionTestUtils.setField(service, "configurationRepository", configuration);
	}

	@Test
	void isConfiguredOnlyWhileTheHostIsSet() {
		assertFalse(service.configured());
		when(configuration.get(ConfigurationKey.MAIL_HOST)).thenReturn("  ");
		assertFalse(service.configured());
		when(configuration.get(ConfigurationKey.MAIL_HOST)).thenReturn("smtp.example.com");
		assertTrue(service.configured());
	}

	@Test
	void buildsTheSenderFromTheCredentials() {
		JavaMailSenderImpl sender = MailService.build("smtp.example.com", 2525, " user@example.com ", "p@ss word", true, true, 3000);
		assertEquals("smtp.example.com", sender.getHost());
		assertEquals(2525, sender.getPort());
		assertEquals("user@example.com", sender.getUsername());
		assertEquals("p@ss word", sender.getPassword());
		assertEquals("true", sender.getJavaMailProperties().get("mail.smtp.auth"));
		assertEquals("3000", sender.getJavaMailProperties().get("mail.smtp.timeout"));
	}

	@Test
	void doesNotLogInWithoutAUserNameOrWhenAuthIsOff() {
		JavaMailSenderImpl noUser = MailService.build("smtp.example.com", 25, "", "ignored", true, false, 5000);
		assertNull(noUser.getUsername());
		assertEquals("false", noUser.getJavaMailProperties().get("mail.smtp.auth"));
		JavaMailSenderImpl authOff = MailService.build("smtp.example.com", 25, "user", "secret", false, false, 5000);
		assertNull(authOff.getUsername());
		assertEquals("false", authOff.getJavaMailProperties().get("mail.smtp.auth"));
	}

	@Test
	void fallsBackToThe587PortWhenTheStoredOneIsMissingOrInvalid() {
		assertEquals(587, MailService.port(null));
		assertEquals(587, MailService.port(""));
		assertEquals(587, MailService.port("abc"));
		assertEquals(587, MailService.port("70000"));
		assertEquals(2525, MailService.port(" 2525 "));
	}

	@Test
	void theSenderAddressFallsBackToTheDefault() {
		assertEquals("no-reply@saasno.local", MailService.address(null));
		assertEquals("no-reply@saasno.local", MailService.address("  "));
		assertEquals("cs@example.com", MailService.address(" cs@example.com "));
	}

	@Test
	void theConfiguredSenderNameWinsOverTheAppName() {
		assertEquals("Support Team", MailService.displayName(" Support Team ", "Acme Portal"));
		assertEquals("Acme Portal", MailService.displayName("", "Acme Portal"));
		assertEquals("Acme Portal", MailService.displayName(null, " Acme Portal "));
		assertEquals("Saasno", MailService.displayName("", null));
	}

	@Test
	void authAndStarttlsAreOnUnlessSetToFalse() {
		assertTrue(MailService.flag(null));
		assertTrue(MailService.flag(""));
		assertTrue(MailService.flag("true"));
		assertFalse(MailService.flag("false"));
		assertFalse(MailService.flag(" FALSE "));
	}
}

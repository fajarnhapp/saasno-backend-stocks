package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.ConfigurationEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.configuration.ConfigurationDTO;
import com.saasno.utility.exception.ApiException;

// The values of the new settings are checked before they are saved.
class ConfigurationServiceTest {

	private ConfigurationRepository repository;
	private SessionUtility sessionUtility;
	private ConfigurationService service;

	@BeforeEach
	void setUp() {
		repository = mock(ConfigurationRepository.class);
		sessionUtility = mock(SessionUtility.class);
		service = new ConfigurationService();
		ReflectionTestUtils.setField(service, "configurationRepository", repository);
		ReflectionTestUtils.setField(service, "sessionUtility", sessionUtility);
		ReflectionTestUtils.setField(service, "changeLogService", mock(ChangeLogService.class));
	}

	private String saved(ConfigurationKey key, String value) {
		service.set(key, value);
		ArgumentCaptor<ConfigurationEntity> captor = ArgumentCaptor.forClass(ConfigurationEntity.class);
		verify(repository).store(captor.capture());
		return captor.getValue().getValue();
	}

	private void session(String ip) {
		SessionEntity session = new SessionEntity();
		session.setIp(ip);
		when(sessionUtility.get()).thenReturn(session);
	}

	@Test
	void appNameIsTrimmedAndLimited() {
		assertEquals("Acme Corp", saved(ConfigurationKey.APP_NAME, "  Acme Corp  "));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.APP_NAME, "   "));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.APP_NAME, "x".repeat(61)));
		verify(repository, never()).store(any());
	}

	@Test
	void brandColourIsKeptInRange() {
		assertEquals("200", saved(ConfigurationKey.BRAND_HUE, "200"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.BRAND_HUE, "361"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.BRAND_HUE, "-1"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.BRAND_HUE, "blue"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.BRAND_CHROMA, "0.1"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.BRAND_CHROMA, "2"));
	}

	@Test
	void chromaIsRoundedToTwoDecimals() {
		assertEquals("0.65", saved(ConfigurationKey.BRAND_CHROMA, "0.654"));
	}

	@Test
	void retentionSettings() {
		assertEquals("0", saved(ConfigurationKey.AUDIT_LOG_RETENTION_DAYS, "0"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.AUDIT_LOG_RETENTION_DAYS, "-5"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.DOWNLOAD_RETENTION_DAYS, "0"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.PASSWORD_RESET_MAX_PER_HOUR, "1.5"));
	}

	@Test
	void anEmptyIpAllowlistIsAllowedButOtherSettingsCannotBeEmpty() {
		assertEquals("", saved(ConfigurationKey.LOGIN_IP_ALLOWLIST, ""));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.FRONTEND_URL, ""));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.FRONTEND_URL, "   "));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.FRONTEND_URL, null));
	}

	@Test
	void anAllowlistWithABadEntryIsRejected() {
		session("203.0.113.7");
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.LOGIN_IP_ALLOWLIST, "203.0.113.7, nonsense"));
	}

	@Test
	void theAdministratorCannotLockThemselvesOut() {
		session("203.0.113.7");
		ApiException e = assertThrows(ApiException.class, () -> service.set(ConfigurationKey.LOGIN_IP_ALLOWLIST, "10.0.0.0/8"));
		assertEquals(true, e.getMessage().contains("203.0.113.7"));
		verify(repository, never()).store(any());
	}

	@Test
	void anAllowlistThatIncludesTheAdministratorIsSaved() {
		session("203.0.113.7");
		assertEquals("10.0.0.0/8, 203.0.113.0/24", saved(ConfigurationKey.LOGIN_IP_ALLOWLIST, "10.0.0.0/8 203.0.113.0/24"));
	}

	@Test
	void maintenanceSettings() {
		assertEquals("true", saved(ConfigurationKey.MAINTENANCE_MODE, "TRUE"));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAINTENANCE_MODE, "yes"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAINTENANCE_MESSAGE, "x".repeat(501)));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAINTENANCE_UNTIL, "tomorrow"));
		verify(repository, never()).store(any());
	}

	@Test
	void maintenanceMessageAndEndTimeMayBeEmptyAndEndTimeIsNormalised() {
		assertEquals("", saved(ConfigurationKey.MAINTENANCE_MESSAGE, ""));
		clearInvocations(repository);
		assertEquals("2026-10-23T18:00", saved(ConfigurationKey.MAINTENANCE_UNTIL, "2026-10-23T18:00:59"));
		clearInvocations(repository);
		assertEquals("", saved(ConfigurationKey.MAINTENANCE_UNTIL, ""));
	}

	@Test
	void theBypassRolesAlwaysIncludeSuper() {
		assertEquals("SUPER", saved(ConfigurationKey.MAINTENANCE_BYPASS_ROLES, ""));
		clearInvocations(repository);
		assertEquals("SUPER,MANAJER", saved(ConfigurationKey.MAINTENANCE_BYPASS_ROLES, "MANAJER"));
		clearInvocations(repository);
		assertEquals("SUPER,GUDANG", saved(ConfigurationKey.MAINTENANCE_BYPASS_ROLES, "GUDANG, SUPER"));
	}


	@Test
	void mailServerSettingsAreChecked() {
		assertEquals("smtp.example.com", saved(ConfigurationKey.MAIL_HOST, "  smtp.example.com  "));
		clearInvocations(repository);
		assertEquals("", saved(ConfigurationKey.MAIL_HOST, ""));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_HOST, "https://smtp.example.com"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_HOST, "smtp.example.com:587"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_HOST, "smtp example"));
		assertEquals("2525", saved(ConfigurationKey.MAIL_PORT, " 2525 "));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PORT, "0"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PORT, "65536"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PORT, ""));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PORT, "smtp"));
		verify(repository, never()).store(any());
	}

	@Test
	void mailCredentialsMayBeEmptyAndAPasswordIsKeptAsTyped() {
		assertEquals("me@example.com", saved(ConfigurationKey.MAIL_USERNAME, "  me@example.com "));
		clearInvocations(repository);
		assertEquals("", saved(ConfigurationKey.MAIL_USERNAME, ""));
		clearInvocations(repository);
		assertEquals(" pa ss ", saved(ConfigurationKey.MAIL_PASSWORD, " pa ss "));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PASSWORD, "a\nb"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PASSWORD, "x".repeat(513)));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_PASSWORD, "********"));
		verify(repository, never()).store(any());
	}

	private ConfigurationDTO passwordDto(String value) {
		ConfigurationDTO dto = new ConfigurationDTO();
		dto.setId(ConfigurationKey.MAIL_PASSWORD);
		dto.setValue(value);
		return dto;
	}

	@Test
	void theSmtpPasswordIsNeverReturned() {
		ConfigurationEntity stored = new ConfigurationEntity(ConfigurationKey.MAIL_PASSWORD.name(), "real-secret");
		when(repository.find(ConfigurationKey.MAIL_PASSWORD.name())).thenReturn(stored);

		// A new password is saved, and the answer shows the mask instead of it.
		ConfigurationDTO answer = service.update(passwordDto("new-secret")).getData();
		assertEquals("new-secret", stored.getValue());
		assertEquals("********", answer.getValue());

		// Sending the mask back changes nothing.
		clearInvocations(repository);
		answer = service.update(passwordDto("********")).getData();
		assertEquals("new-secret", stored.getValue());
		assertEquals("********", answer.getValue());

		// Emptying it removes the password, and an empty password shows as empty (not as a mask).
		answer = service.update(passwordDto("")).getData();
		assertEquals("", stored.getValue());
		assertEquals("", answer.getValue());

		// Other settings are not masked.
		ConfigurationDTO host = new ConfigurationDTO();
		host.setId(ConfigurationKey.MAIL_HOST);
		host.setValue("smtp.example.com");
		assertEquals("smtp.example.com", ConfigurationService.masked(host).getValue());
	}


	@Test
	void mailSenderAndConnectionSettingsAreChecked() {
		assertEquals("cs@example.com", saved(ConfigurationKey.MAIL_FROM, " cs@example.com "));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_FROM, ""));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_FROM, "not-an-address"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_FROM, "a@b"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_FROM, "Name <a@b.com>"));
		assertEquals("Support Team", saved(ConfigurationKey.MAIL_FROM_NAME, "  Support Team "));
		clearInvocations(repository);
		assertEquals("", saved(ConfigurationKey.MAIL_FROM_NAME, ""));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_FROM_NAME, "x".repeat(61)));
		assertEquals("false", saved(ConfigurationKey.MAIL_AUTH, "FALSE"));
		clearInvocations(repository);
		assertEquals("true", saved(ConfigurationKey.MAIL_STARTTLS, "true"));
		clearInvocations(repository);
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_AUTH, "yes"));
		assertThrows(ApiException.class, () -> service.set(ConfigurationKey.MAIL_STARTTLS, ""));
		verify(repository, never()).store(any());
	}
}

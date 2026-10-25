package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.EmailTemplateEntity;
import com.saasno.repository.EmailTemplateRepository;
import com.saasno.utility.dto.emailtemplate.EmailTemplateDTO;
import com.saasno.utility.exception.ApiException;

class EmailTemplateServiceTest {

	private EmailTemplateRepository repository;
	private EmailTemplateService service;

	@BeforeEach
	void setUp() {
		repository = mock(EmailTemplateRepository.class);
		service = new EmailTemplateService();
		ReflectionTestUtils.setField(service, "emailTemplateRepository", repository);
		ReflectionTestUtils.setField(service, "changeLogService", mock(ChangeLogService.class));
	}

	private EmailTemplateDTO dto(String subject) {
		EmailTemplateDTO dto = new EmailTemplateDTO();
		dto.setId(EmailTemplateService.PASSWORD_RESET);
		dto.setSubject(subject);
		dto.setHeading("Heading");
		dto.setIntro("Intro");
		dto.setButtonLabel("Go");
		dto.setNote("");
		return dto;
	}

	@Test
	void placeholdersAreFilledAndUnknownOnesStayVisible() {
		assertEquals("Hi Budi, welcome to Saasno {{missing}}",
				EmailTemplateService.fill("Hi {{ name }}, welcome to {{appName}} {{missing}}", Map.of("name", "Budi", "appName", "Saasno")));
	}

	@Test
	void valuesWithRegexCharactersAreInsertedAsTheyAre() {
		assertEquals("Pay $1 \\ now", EmailTemplateService.fill("Pay {{x}} now", Map.of("x", "$1 \\")));
	}

	@Test
	void withoutACustomRowTheBuiltInTextIsUsed() {
		EmailTemplateService.Text text = service.resolve(EmailTemplateService.PASSWORD_RESET, Map.of("appName", "Acme", "lifetime", "30 minutes"));
		assertEquals("Reset your Acme password", text.subject());
		assertTrue(text.note().contains("30 minutes"));
	}

	@Test
	void aCustomRowWinsAndTheSubjectStaysOnOneLine() {
		EmailTemplateEntity custom = new EmailTemplateEntity(EmailTemplateService.PASSWORD_RESET);
		custom.setSubject("New password\nfor {{appName}}");
		custom.setHeading("H");
		custom.setIntro("I");
		custom.setButtonLabel("B");
		custom.setNote("");
		when(repository.find(EmailTemplateService.PASSWORD_RESET)).thenReturn(custom);
		EmailTemplateService.Text text = service.resolve(EmailTemplateService.PASSWORD_RESET, Map.of("appName", "Acme"));
		assertEquals("New password for Acme", text.subject());
	}

	@Test
	void anUnknownPlaceholderIsRefused() {
		assertThrows(ApiException.class, () -> service.update(dto("Hello {{password}}")));
		verify(repository, never()).store(any());
	}

	@Test
	void aPlaceholderOfAnotherTemplateIsRefused() {
		// {{lifetime}} is not offered for the sign-in alert.
		EmailTemplateDTO other = dto("Alert {{lifetime}}");
		other.setId(EmailTemplateService.NEW_DEVICE_LOGIN);
		assertThrows(ApiException.class, () -> service.update(other));
	}

	@Test
	void requiredPartsCannotBeEmpty() {
		assertThrows(ApiException.class, () -> service.update(dto("  ")));
	}

	@Test
	void anUnknownTemplateIsRefused() {
		EmailTemplateDTO dto = dto("Hi");
		dto.setId("NOPE");
		assertThrows(ApiException.class, () -> service.update(dto));
	}
}

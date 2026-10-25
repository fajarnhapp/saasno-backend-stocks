package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.ChangeLogEntity;
import com.saasno.repository.ChangeLogRepository;
import com.saasno.utility.SessionUtility;

class ChangeLogServiceTest {

	private ChangeLogRepository repository;
	private ChangeLogService service;

	@BeforeEach
	void setUp() {
		repository = mock(ChangeLogRepository.class);
		SessionUtility session = mock(SessionUtility.class);
		when(session.getUsername()).thenReturn("admin");
		service = new ChangeLogService();
		ReflectionTestUtils.setField(service, "changeLogRepository", repository);
		ReflectionTestUtils.setField(service, "sessionUtility", session);
	}

	@Test
	void onlyFieldsThatDifferAreRecorded() {
		service.updated("USER", "u1", "budi", ChangeLogService.changes().add("status", "ACTIVED", "BLOCKED").add("username", "budi", "budi"));
		ArgumentCaptor<ChangeLogEntity> saved = ArgumentCaptor.forClass(ChangeLogEntity.class);
		verify(repository).store(saved.capture());
		assertEquals("status", saved.getValue().getField());
		assertEquals("ACTIVED", saved.getValue().getOldValue());
		assertEquals("BLOCKED", saved.getValue().getNewValue());
		assertEquals("admin", saved.getValue().getChangedBy());
	}

	@Test
	void nothingIsStoredWhenNothingChanged() {
		service.updated("USER", "u1", "budi", ChangeLogService.changes().add("status", "ACTIVED", "ACTIVED"));
		verify(repository, never()).store(any());
	}

	@Test
	void secretValuesAreNeverStored() {
		service.updated("CONFIGURATION", "MAIL_PASSWORD", "MAIL_PASSWORD", ChangeLogService.changes().add("MAIL_PASSWORD", "old-secret", "new-secret"));
		ArgumentCaptor<ChangeLogEntity> saved = ArgumentCaptor.forClass(ChangeLogEntity.class);
		verify(repository).store(saved.capture());
		assertEquals("********", saved.getValue().getOldValue());
		assertEquals("********", saved.getValue().getNewValue());
	}

	@Test
	void creationAndDeletionHaveNoField() {
		service.created("ROLE", "r1", "Admin");
		ArgumentCaptor<ChangeLogEntity> saved = ArgumentCaptor.forClass(ChangeLogEntity.class);
		verify(repository).store(saved.capture());
		assertEquals("CREATE", saved.getValue().getAction());
		assertNull(saved.getValue().getField());
	}

	@Test
	void aFailingStoreDoesNotBreakTheChange() {
		org.mockito.Mockito.doThrow(new RuntimeException("db down")).when(repository).store(any());
		service.deleted("ROLE", "r1", "Admin");
	}
}

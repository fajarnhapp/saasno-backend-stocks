package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.DeletionRequestEntity;
import com.saasno.entity.PersonEntity;
import com.saasno.entity.PrivacyConsentEntity;
import com.saasno.entity.PrivacyPolicyEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.PrivacyRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.privacy.PrivacyDTO;
import com.saasno.utility.exception.ApiException;

class PrivacyServiceTest {

	private PrivacyRepository repository;
	private UserService userService;
	private NotificationPublisher notifications;
	private PrivacyService service;
	private UserEntity user;

	@BeforeEach
	void setUp() {
		repository = mock(PrivacyRepository.class);
		userService = mock(UserService.class);
		notifications = mock(NotificationPublisher.class);
		UserRepository users = mock(UserRepository.class);
		SessionUtility session = mock(SessionUtility.class);
		when(session.getUsername()).thenReturn("budi");
		service = new PrivacyService();
		ReflectionTestUtils.setField(service, "privacyRepository", repository);
		ReflectionTestUtils.setField(service, "userRepository", users);
		ReflectionTestUtils.setField(service, "sessionUtility", session);
		ReflectionTestUtils.setField(service, "userService", userService);
		ReflectionTestUtils.setField(service, "notificationPublisher", notifications);
		ReflectionTestUtils.setField(service, "changeLogService", mock(ChangeLogService.class));
		ReflectionTestUtils.setField(service, "loginHistoryRepository", mock(com.saasno.repository.LoginHistoryRepository.class));
		ReflectionTestUtils.setField(service, "apiTokenRepository", mock(com.saasno.repository.ApiTokenRepository.class));

		user = new UserEntity("budi", new PersonEntity("Budi"), Set.of());
		user.beforeInsert();
		when(users.findByUsername("budi")).thenReturn(user);
		when(repository.consents(any())).thenReturn(List.of());
	}

	@Test
	void withoutAPublishedVersionTheBuiltInTextIsUsedInTheAskedLanguage() {
		PrivacyDTO.Policy indonesian = service.current("id");
		assertEquals(0, indonesian.version());
		assertTrue(indonesian.builtIn());
		assertTrue(indonesian.content().contains("Kebijakan Privasi"));
		assertTrue(service.current("en").content().contains("Privacy Policy"));
		assertTrue(service.current("xx").content().contains("Privacy Policy"));
	}

	@Test
	void aPublishedVersionWinsOverTheBuiltInText() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(3, "Our own text", null, "admin"));
		PrivacyDTO.Policy policy = service.current("id");
		assertEquals(3, policy.version());
		assertEquals("Our own text", policy.content());
		assertFalse(policy.builtIn());
	}

	@Test
	void aTextInOnlyOneLanguageIsShownInBothAndHasNoLanguageChoice() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(1, "English only", null, "admin"));
		PrivacyDTO.Policy indonesian = service.current("id");
		assertEquals("English only", indonesian.content());
		assertFalse(indonesian.bilingual());

		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(1, null, "Hanya Indonesia", "admin"));
		assertEquals("Hanya Indonesia", service.current("en").content());
		assertFalse(service.current("en").bilingual());
	}

	@Test
	void aTextInBothLanguagesFollowsTheAskedLanguage() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(1, "English text", "Teks Indonesia", "admin"));
		assertEquals("Teks Indonesia", service.current("id").content());
		assertEquals("English text", service.current("en").content());
		assertTrue(service.current("id").bilingual());
	}

	@Test
	void theBuiltInTextHasBothLanguages() {
		assertTrue(service.current("en").bilingual());
		var admin = service.admin().getData();
		assertTrue(admin.builtIn());
		assertTrue(admin.contentEn().contains("Privacy Policy"));
		assertTrue(admin.contentId().contains("Kebijakan Privasi"));
	}

	@Test
	void bothTextsArePublishedTogether() {
		service.publish(new PrivacyDTO.Publish(" English ", " Indonesia "));
		ArgumentCaptor<Object> saved = ArgumentCaptor.forClass(Object.class);
		verify(repository).store(saved.capture());
		PrivacyPolicyEntity entity = (PrivacyPolicyEntity) saved.getValue();
		assertEquals("English", entity.getContent());
		assertEquals("Indonesia", entity.getContentId());
	}

	@Test
	void changingOnlyOneLanguageIsANewVersion() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(1, "English", "Indonesia", "admin"));
		service.publish(new PrivacyDTO.Publish("English", "Indonesia baru"));
		verify(repository).store(any(PrivacyPolicyEntity.class));
		assertThrows(ApiException.class, () -> service.publish(new PrivacyDTO.Publish("English", "Indonesia")));
	}

	@Test
	void publishingCreatesTheNextVersion() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(2, "old", null, "admin"));
		service.publish(new PrivacyDTO.Publish("  new text\r\nline two  ", null));
		ArgumentCaptor<Object> saved = ArgumentCaptor.forClass(Object.class);
		verify(repository).store(saved.capture());
		PrivacyPolicyEntity entity = (PrivacyPolicyEntity) saved.getValue();
		assertEquals(3, entity.getVersion());
		assertEquals("new text\nline two", entity.getContent());
		assertEquals("budi", entity.getPublishedBy());
	}

	@Test
	void theFirstPublishedVersionIsOne() {
		service.publish(new PrivacyDTO.Publish("first", null));
		ArgumentCaptor<Object> saved = ArgumentCaptor.forClass(Object.class);
		verify(repository).store(saved.capture());
		assertEquals(1, ((PrivacyPolicyEntity) saved.getValue()).getVersion());
	}

	@Test
	void emptyTooLongOrUnchangedTextIsRefused() {
		assertThrows(ApiException.class, () -> service.publish(new PrivacyDTO.Publish("   ", null)));
		assertThrows(ApiException.class, () -> service.publish(null));
		assertThrows(ApiException.class, () -> service.publish(new PrivacyDTO.Publish("x".repeat(PrivacyService.MAX_CONTENT + 1), null)));
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(1, "same", null, "admin"));
		assertThrows(ApiException.class, () -> service.publish(new PrivacyDTO.Publish("same", null)));
		verify(repository, never()).store(any());
	}

	@Test
	void nobodyIsAskedToAcceptTheBuiltInText() {
		assertFalse(service.status().getData().needsAcceptance());
	}

	@Test
	void aNewVersionHasToBeAcceptedOnce() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(2, "text", null, "admin"));
		assertTrue(service.status().getData().needsAcceptance());

		when(repository.consent(user.getId(), 2)).thenReturn(new PrivacyConsentEntity(user.getId(), 2));
		assertFalse(service.status().getData().needsAcceptance());
	}

	@Test
	void acceptingTwiceStoresOneRecord() {
		when(repository.latestPolicy()).thenReturn(new PrivacyPolicyEntity(2, "text", null, "admin"));
		service.accept();
		verify(repository).store(any(PrivacyConsentEntity.class));

		when(repository.consent(user.getId(), 2)).thenReturn(new PrivacyConsentEntity(user.getId(), 2));
		service.accept();
		verify(repository).store(any(PrivacyConsentEntity.class));
	}

	@Test
	void aDeletionRequestNotifiesTheAdminsAndCannotBeRepeated() {
		when(repository.userIdsWithRole("SUPER")).thenReturn(List.of("admin-1"));
		service.requestDeletion();
		verify(repository).store(any(DeletionRequestEntity.class));
		verify(notifications).notify(any(), any(), any(), any(), any());

		when(repository.pendingRequest(user.getId())).thenReturn(new DeletionRequestEntity(user.getId(), "budi"));
		assertThrows(ApiException.class, () -> service.requestDeletion());
	}

	@Test
	void approvingMovesTheUserToTheTrashAndRejectingDoesNot() {
		DeletionRequestEntity approve = new DeletionRequestEntity("u-1", "someone");
		when(repository.request("r-1")).thenReturn(approve);
		service.handle("r-1", "approve");
		verify(userService).delete("u-1");
		assertEquals(DeletionRequestEntity.DONE, approve.getStatus());

		DeletionRequestEntity reject = new DeletionRequestEntity("u-2", "other");
		when(repository.request("r-2")).thenReturn(reject);
		service.handle("r-2", "REJECT");
		verify(userService, never()).delete("u-2");
		assertEquals(DeletionRequestEntity.REJECTED, reject.getStatus());
	}

	@Test
	void aHandledOrUnknownRequestCannotBeHandledAgain() {
		DeletionRequestEntity done = new DeletionRequestEntity("u-1", "someone");
		done.handle(DeletionRequestEntity.DONE, "admin");
		when(repository.request("r-1")).thenReturn(done);
		assertThrows(ApiException.class, () -> service.handle("r-1", "APPROVE"));
		assertThrows(ApiException.class, () -> service.handle("nope", "APPROVE"));
		DeletionRequestEntity pending = new DeletionRequestEntity("u-3", "x");
		when(repository.request("r-3")).thenReturn(pending);
		assertThrows(ApiException.class, () -> service.handle("r-3", "MAYBE"));
		assertNull(pending.getHandledAt());
	}

	@Test
	void theExportHoldsTheOwnDataButNoSecrets() {
		var data = service.exportMyData().getData();
		assertEquals("budi", ((java.util.Map<?, ?>) data.get("account")).get("username"));
		assertTrue(data.containsKey("person") && data.containsKey("contacts") && data.containsKey("signInHistory"));
		String all = data.toString().toLowerCase();
		assertFalse(all.contains("password"));
		assertFalse(all.contains("secret"));
		assertFalse(all.contains("tokenhash"));
	}
}

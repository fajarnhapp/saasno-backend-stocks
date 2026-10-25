package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.SessionEntity;
import com.saasno.utility.SessionUtility;

class SessionManagementServiceTest {

	private SessionService sessions;
	private SessionManagementService service;
	private SessionEntity mine;
	private SessionEntity myOther;
	private SessionEntity theirs;
	private SessionEntity theirsToo;

	private static SessionEntity session(String id, String userId, String username) {
		return new SessionEntity(id, userId, username, Set.of("SUPER"), new Date(), new Date());
	}

	@BeforeEach
	void setUp() {
		sessions = mock(SessionService.class);
		SessionUtility utility = mock(SessionUtility.class);
		mine = session("admin:1", "u-admin", "admin");
		myOther = session("admin:2", "u-admin", "admin");
		theirs = session("budi:1", "u-budi", "budi");
		theirsToo = session("siti:1", "u-siti", "siti");
		when(utility.get()).thenReturn(mine);
		when(sessions.readAll()).thenReturn(List.of(mine, myOther, theirs, theirsToo));
		service = new SessionManagementService();
		ReflectionTestUtils.setField(service, "sessionService", sessions);
		ReflectionTestUtils.setField(service, "sessionUtility", utility);
		ReflectionTestUtils.setField(service, "changeLogService", mock(ChangeLogService.class));
	}

	@Test
	void everyoneIsSignedOutExceptTheSessionInUse() {
		assertEquals(3, service.revokeAll(null).getData());
		verify(sessions).delete("admin:2");
		verify(sessions).delete("budi:1");
		verify(sessions).delete("siti:1");
		verify(sessions, never()).delete("admin:1");
	}

	@Test
	void aBlankUserIdMeansEveryone() {
		assertEquals(3, service.revokeAll("  ").getData());
	}

	@Test
	void oneUserCanBeSignedOutEverywhere() {
		assertEquals(1, service.revokeAll("u-budi").getData());
		verify(sessions).delete("budi:1");
		verify(sessions, never()).delete("siti:1");
	}

	@Test
	void anAdminSigningOutThemselvesKeepsTheCurrentSession() {
		assertEquals(1, service.revokeAll("u-admin").getData());
		verify(sessions).delete("admin:2");
		verify(sessions, never()).delete("admin:1");
	}

	@Test
	void nothingToSignOutIsNotAnError() {
		assertEquals(0, service.revokeAll("someone-else").getData());
		verify(sessions, never()).delete(any());
	}
}

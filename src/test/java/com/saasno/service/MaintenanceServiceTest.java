package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.utility.dto.maintenance.CleanupResultDTO;
import com.saasno.utility.exception.ApiException;

// Which cleanup runs for which "type".
class MaintenanceServiceTest {

	private MaintenanceService service;
	private NotificationCleanupJob notifications;

	@BeforeEach
	void setUp() {
		notifications = mock(NotificationCleanupJob.class);
		when(notifications.runNow()).thenReturn(3);
		service = spy(new MaintenanceService());
		ReflectionTestUtils.setField(service, "notificationCleanupJob", notifications);
		doReturn(5L).when(service).cleanupDownloads();
		doReturn(7L).when(service).cleanupAuditLogs();
	}

	@Test
	void auditLogsAreKeptForOneYearUnlessConfigured() {
		org.junit.jupiter.api.Assertions.assertEquals(365, MaintenanceService.DEFAULT_AUDIT_LOG_DAYS);
	}

	@Test
	void everythingWhenNoTypeIsGiven() {
		assertEquals(new CleanupResultDTO(5, 7, 3), service.run(null));
		assertEquals(new CleanupResultDTO(5, 7, 3), service.run(""));
		assertEquals(new CleanupResultDTO(5, 7, 3), service.run(" ALL "));
	}

	@Test
	void oneKindOnly() {
		assertEquals(new CleanupResultDTO(5, 0, 0), service.run("downloads"));
		verify(service, never()).cleanupAuditLogs();
		verify(notifications, never()).runNow();
	}

	@Test
	void auditLogsOnly() {
		assertEquals(new CleanupResultDTO(0, 7, 0), service.run("Audit-Logs"));
		verify(service, never()).cleanupDownloads();
		verify(notifications, never()).runNow();
	}

	@Test
	void notificationsOnly() {
		assertEquals(new CleanupResultDTO(0, 0, 3), service.run("notifications"));
		verify(service, never()).cleanupDownloads();
		verify(service, never()).cleanupAuditLogs();
	}

	@Test
	void anUnknownTypeIsRefusedAndNothingRuns() {
		ApiException e = assertThrows(ApiException.class, () -> service.run("everything-else"));
		assertEquals("400", e.getCode());
		verify(service, never()).cleanupDownloads();
		verify(service, never()).cleanupAuditLogs();
		verify(notifications, never()).runNow();
	}

}

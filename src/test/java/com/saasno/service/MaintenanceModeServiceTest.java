package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.RoleEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.maintenance.MaintenanceModeUpdateDTO;
import com.saasno.utility.exception.ApiException;

import jakarta.persistence.EntityManager;

class MaintenanceModeServiceTest {

	private ConfigurationRepository configuration;
	private ConfigurationService configurationService;
	private EntityManager entityManager;
	private MaintenanceModeService service;

	@BeforeEach
	void setUp() {
		configuration = mock(ConfigurationRepository.class);
		configurationService = mock(ConfigurationService.class);
		entityManager = mock(EntityManager.class);
		service = new MaintenanceModeService();
		ReflectionTestUtils.setField(service, "configurationRepository", configuration);
		ReflectionTestUtils.setField(service, "configurationService", configurationService);
		ReflectionTestUtils.setField(service, "entityManager", entityManager);
	}

	private void config(String mode, String until, String roles, String message) {
		when(configuration.get(ConfigurationKey.MAINTENANCE_MODE)).thenReturn(mode);
		when(configuration.get(ConfigurationKey.MAINTENANCE_UNTIL)).thenReturn(until);
		when(configuration.get(ConfigurationKey.MAINTENANCE_BYPASS_ROLES)).thenReturn(roles);
		when(configuration.get(ConfigurationKey.MAINTENANCE_MESSAGE)).thenReturn(message);
		service.invalidate();
	}

	private static String in(long hours) {
		return MaintenanceModeService.formatUntil(LocalDateTime.now().plusHours(hours));
	}

	@Test
	void isOffByDefault() {
		config(null, null, null, null);
		assertFalse(service.active());
		assertDoesNotThrow(() -> service.enforce(List.of("KASIR"), "/api/menu", "GET"));
	}

	@Test
	void blocksEveryoneExceptTheAllowedRoles() {
		config("true", "", "SUPER,MANAJER", "Back at 18:00");
		assertTrue(service.active());
		ApiException blocked = assertThrows(ApiException.class, () -> service.enforce(List.of("KASIR"), "/api/menu", "GET"));
		assertEquals("503", blocked.getCode());
		assertEquals("Back at 18:00", blocked.getMessage());
		assertDoesNotThrow(() -> service.enforce(List.of("MANAJER"), "/api/menu", "GET"));
		assertDoesNotThrow(() -> service.enforce(List.of("KASIR", "MANAJER"), "/api/menu", "GET"));
	}

	@Test
	void superIsAlwaysAllowedEvenWhenTheListIsEmptyOrMissing() {
		config("true", "", "", "");
		assertDoesNotThrow(() -> service.enforce(List.of("SUPER"), "/api/menu", "GET"));
		config("true", "", null, "");
		assertDoesNotThrow(() -> service.enforce(List.of("SUPER"), "/api/menu", "GET"));
		assertThrows(ApiException.class, () -> service.enforce(List.of("KASIR"), "/api/menu", "GET"));
	}

	@Test
	void aUserWithoutAnyRoleIsBlocked() {
		config("true", "", "SUPER", "");
		assertThrows(ApiException.class, () -> service.enforce(List.of(), "/api/menu", "GET"));
		assertThrows(ApiException.class, () -> service.enforce(null, "/api/menu", "GET"));
	}

	@Test
	void usesADefaultTextWhenThereIsNoMessage() {
		config("true", "", "SUPER", "   ");
		ApiException blocked = assertThrows(ApiException.class, () -> service.enforce(List.of("KASIR"), "/api/menu", "GET"));
		assertEquals(MaintenanceModeService.DEFAULT_MESSAGE, blocked.getMessage());
	}

	@Test
	void signingOutIsAlwaysPossible() {
		config("true", "", "SUPER", "");
		assertDoesNotThrow(() -> service.enforce(List.of("KASIR"), "/api/logout", "DELETE"));
		assertThrows(ApiException.class, () -> service.enforce(List.of("KASIR"), "/api/logout", "GET"));
	}

	@Test
	void endsByItselfWhenTheEndTimeHasPassed() {
		config("true", MaintenanceModeService.formatUntil(LocalDateTime.now().minusMinutes(5)), "SUPER", "x");
		assertFalse(service.active());
		assertDoesNotThrow(() -> service.enforce(List.of("KASIR"), "/api/menu", "GET"));
		assertFalse(service.status().active());
	}

	@Test
	void staysOnBeforeTheEndTime() {
		config("true", in(2), "SUPER", "x");
		assertTrue(service.active());
		assertTrue(service.status().active());
		assertEquals("x", service.status().message());
		assertTrue(service.status().until() != null);
	}

	@Test
	void theStatusSaysNothingWhileItIsOff() {
		config("false", "", "SUPER", "secret note");
		assertFalse(service.status().active());
		assertEquals("", service.status().message());
		assertNull(service.status().until());
	}

	@Test
	void theStateIsCachedBrieflyAndCanBeRefreshed() {
		config("true", "", "SUPER", "x");
		assertTrue(service.active());
		when(configuration.get(ConfigurationKey.MAINTENANCE_MODE)).thenReturn("false");
		assertTrue(service.active(), "still the cached answer");
		service.invalidate();
		assertFalse(service.active());
	}

	@Test
	void theBypassValueAlwaysStartsWithSuperWithoutDuplicates() {
		assertEquals("SUPER", MaintenanceModeService.bypassRolesValue(List.of()));
		assertEquals("SUPER,MANAJER", MaintenanceModeService.bypassRolesValue(List.of("MANAJER", "SUPER", " MANAJER ")));
		assertEquals("SUPER,A,B", MaintenanceModeService.bypassRolesValue(List.of("A", "", "B")));
	}

	@Test
	void parsesTheEndTime() {
		assertEquals(LocalDateTime.of(2026, 10, 23, 18, 0), MaintenanceModeService.parseUntil("2026-10-23T18:00"));
		assertEquals(LocalDateTime.of(2026, 10, 23, 18, 0), MaintenanceModeService.parseUntil(" 2026-10-23T18:00:45 "));
		assertNull(MaintenanceModeService.parseUntil(""));
		assertNull(MaintenanceModeService.parseUntil("tomorrow"));
		assertEquals("2026-10-23T18:00", MaintenanceModeService.formatUntil(LocalDateTime.of(2026, 10, 23, 18, 0)));
	}

	@Test
	void updateRefusesAnEndTimeInThePastOrABadOne() {
		assertThrows(ApiException.class, () -> service.update(new MaintenanceModeUpdateDTO(true, "m", MaintenanceModeService.formatUntil(LocalDateTime.now().minusHours(1)), List.of())));
		assertThrows(ApiException.class, () -> service.update(new MaintenanceModeUpdateDTO(true, "m", "soon", List.of())));
		assertThrows(ApiException.class, () -> service.update(null));
		verify(configurationService, never()).set(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

	@Test
	void updateRefusesAnUnknownRole() {
		when(entityManager.find(RoleEntity.class, "GHOST")).thenReturn(null);
		assertThrows(ApiException.class, () -> service.update(new MaintenanceModeUpdateDTO(true, "m", "", List.of("GHOST"))));
		verify(configurationService, never()).set(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
	}

}

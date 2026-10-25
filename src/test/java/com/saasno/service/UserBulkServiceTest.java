package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.entity.PersonEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.dto.user.UserBulkDTO;
import com.saasno.utility.exception.ApiException;

class UserBulkServiceTest {

	private UserService userService;
	private UserRepository userRepository;
	private RoleRepository roleRepository;
	private UserBulkService service;

	@BeforeEach
	void setUp() {
		userService = mock(UserService.class);
		userRepository = mock(UserRepository.class);
		roleRepository = mock(RoleRepository.class);
		service = new UserBulkService();
		ReflectionTestUtils.setField(service, "userService", userService);
		ReflectionTestUtils.setField(service, "userRepository", userRepository);
		ReflectionTestUtils.setField(service, "roleRepository", roleRepository);

		RoleEntity admin = new RoleEntity();
		admin.setId("ADMINISTRASI");
		admin.setName("Administrasi");
		when(roleRepository.findAllSafe()).thenReturn(List.of(admin));
	}

	private UserBulkDTO.ImportRow row(String username, String name, String email, String role) {
		return new UserBulkDTO.ImportRow(username, name, email, role);
	}

	@Test
	void aDryRunChecksEveryRowAndCreatesNothing() {
		var result = service.importUsers(new UserBulkDTO.ImportRequest(List.of(
				row("budi.s", "Budi", "budi@example.com", "administrasi"),
				row("Bad Name", "X", "x@example.com", "ADMINISTRASI"),
				row("siti", "Siti", "not-an-email", "ADMINISTRASI"),
				row("andi", "Andi", "andi@example.com", "NoSuchRole")), true)).getData();

		assertEquals(1, result.valid());
		assertEquals(3, result.invalid());
		assertEquals(0, result.created());
		assertTrue(result.items().get(0).ok());
		assertFalse(result.items().get(1).ok());
		// Line numbers count the header: the first row is line 2.
		assertEquals(2, result.items().get(0).line());
		verify(userService, never()).create(any());
	}

	@Test
	void theRoleCanBeNamedByIdOrName() {
		var result = service.importUsers(new UserBulkDTO.ImportRequest(List.of(
				row("a.one", "A", "a@example.com", "ADMINISTRASI"), row("b.two", "B", "b@example.com", "Administrasi")), true)).getData();
		assertEquals(2, result.valid());
	}

	@Test
	void repeatsInsideTheFileAreCaught() {
		var result = service.importUsers(new UserBulkDTO.ImportRequest(List.of(
				row("same", "A", "a@example.com", "ADMINISTRASI"), row("same", "B", "b@example.com", "ADMINISTRASI"),
				row("other", "C", "A@example.com", "ADMINISTRASI")), true)).getData();
		assertEquals(1, result.valid());
		assertFalse(result.items().get(1).ok());
		assertFalse(result.items().get(2).ok());
	}

	@Test
	void existingUsernamesAndEmailsAreCaught() {
		UserEntity taken = new UserEntity("taken", new PersonEntity("T"), Set.of());
		when(userRepository.findByUsername("taken")).thenReturn(taken);
		when(userRepository.emailTaken("used@example.com", null)).thenReturn(true);
		var result = service.importUsers(new UserBulkDTO.ImportRequest(List.of(
				row("taken", "T", "t@example.com", "ADMINISTRASI"), row("fresh", "F", "used@example.com", "ADMINISTRASI")), true)).getData();
		assertEquals(0, result.valid());
	}

	@Test
	void aRealImportCreatesTheValidRowsAndReportsTheOthers() {
		var result = service.importUsers(new UserBulkDTO.ImportRequest(List.of(
				row("good.one", "Good", "good@example.com", "ADMINISTRASI"), row("Bad", "X", "x@example.com", "ADMINISTRASI")), false)).getData();
		assertEquals(1, result.created());
		assertEquals(1, result.invalid());
		verify(userService).create(any());
	}

	@Test
	void anEmptyOrTooLongFileIsRefused() {
		assertThrows(ApiException.class, () -> service.importUsers(new UserBulkDTO.ImportRequest(List.of(), true)));
		List<UserBulkDTO.ImportRow> many = java.util.Collections.nCopies(UserBulkService.MAX_ITEMS + 1, row("a", "b", "c@d.ee", "x"));
		assertThrows(ApiException.class, () -> service.importUsers(new UserBulkDTO.ImportRequest(many, true)));
	}

	@Test
	void bulkActionsAreChecked() {
		assertThrows(ApiException.class, () -> service.act(new UserBulkDTO.Action(List.of(), "BLOCK", null)));
		assertThrows(ApiException.class, () -> service.act(new UserBulkDTO.Action(List.of("1"), "EXPLODE", null)));
		assertThrows(ApiException.class, () -> service.act(new UserBulkDTO.Action(List.of("1"), "SET_ROLE", "NOPE")));
		verify(userService, never()).bulkApply(any(), any(), any());
	}

	@Test
	void oneFailureDoesNotStopTheOthers() {
		when(userService.bulkApply("1", "BLOCK", null)).thenReturn("one");
		when(userService.bulkApply("2", "BLOCK", null)).thenThrow(new ApiException("400", "You cannot block your own account"));
		when(userService.bulkApply("3", "BLOCK", null)).thenReturn("three");
		var result = service.act(new UserBulkDTO.Action(List.of("1", "2", "3"), "block", null)).getData();
		assertEquals(2, result.succeeded());
		assertEquals(1, result.failed());
		assertEquals("You cannot block your own account", result.items().get(1).message());
	}
}

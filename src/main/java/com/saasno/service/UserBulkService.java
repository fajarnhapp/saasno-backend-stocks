package com.saasno.service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.RoleEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.dto.role.RoleDTO;
import com.saasno.utility.dto.user.UserBulkDTO;
import com.saasno.utility.dto.user.UserDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

// Many users at once: actions on a selection, and creating users from a list (a CSV file read by the browser). Every user is handled in
// its own transaction, so one that fails does not undo the others, and the answer says which succeeded and which did not.
// Deliberately not @Transactional itself.
@Service
public class UserBulkService {

	static final int MAX_ITEMS = 200;

	@Autowired
	private UserService userService;

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	public ResponseModel<UserBulkDTO.ActionResult> act(UserBulkDTO.Action request) {
		if (request == null || request.ids() == null || request.ids().isEmpty()) {
			throw new ApiException("400", "Select at least one user");
		}
		if (request.ids().size() > MAX_ITEMS) {
			throw new ApiException("400", "At most " + MAX_ITEMS + " users at a time");
		}
		String action = request.action() == null ? "" : request.action().trim().toUpperCase(Locale.ROOT);
		if (!UserService.BULK_ACTIONS.contains(action)) {
			throw new ApiException("400", "Unknown action: " + request.action());
		}
		if (action.equals("SET_ROLE") && roleRepository.find(request.roleId()) == null) {
			throw new ApiException("400", "Choose a role that exists");
		}

		List<UserBulkDTO.ActionItem> items = new ArrayList<>();
		int succeeded = 0;
		for (String id : new java.util.LinkedHashSet<>(request.ids())) {
			try {
				String username = userService.bulkApply(id, action, request.roleId());
				items.add(new UserBulkDTO.ActionItem(id, username, true, "Done"));
				succeeded++;
			} catch (ApiException e) {
				items.add(new UserBulkDTO.ActionItem(id, null, false, e.getMessage()));
			} catch (RuntimeException e) {
				items.add(new UserBulkDTO.ActionItem(id, null, false, "Failed"));
			}
		}
		return new ResponseModel<UserBulkDTO.ActionResult>().success(new UserBulkDTO.ActionResult(succeeded, items.size() - succeeded, items));
	}

	public ResponseModel<UserBulkDTO.ImportResult> importUsers(UserBulkDTO.ImportRequest request) {
		if (request == null || request.rows() == null || request.rows().isEmpty()) {
			throw new ApiException("400", "The file has no rows");
		}
		if (request.rows().size() > MAX_ITEMS) {
			throw new ApiException("400", "At most " + MAX_ITEMS + " rows at a time");
		}

		List<RoleEntity> roles = roleRepository.findAllSafe();
		Set<String> usernames = new HashSet<>();
		Set<String> emails = new HashSet<>();
		List<UserBulkDTO.ImportItem> items = new ArrayList<>();
		int valid = 0;
		int created = 0;
		int line = 1;
		for (UserBulkDTO.ImportRow row : request.rows()) {
			line++; // line 1 of the file is the header
			String username = row.username() == null ? "" : row.username().trim();
			try {
				RoleEntity role = findRole(roles, row.role());
				UserDTO dto = check(row, username, role, usernames, emails);
				valid++;
				if (!request.dryRun()) {
					userService.create(dto);
					created++;
					items.add(new UserBulkDTO.ImportItem(line, username, true, "Created"));
				} else {
					items.add(new UserBulkDTO.ImportItem(line, username, true, "OK"));
				}
			} catch (ApiException e) {
				items.add(new UserBulkDTO.ImportItem(line, username, false, e.getMessage()));
			} catch (RuntimeException e) {
				items.add(new UserBulkDTO.ImportItem(line, username, false, "Failed"));
			}
		}
		return new ResponseModel<UserBulkDTO.ImportResult>().success(
				new UserBulkDTO.ImportResult(request.dryRun(), valid, items.size() - valid, created, items));
	}

	// Checks one row; the username and email must also be new within the file.
	private UserDTO check(UserBulkDTO.ImportRow row, String username, RoleEntity role, Set<String> usernames, Set<String> emails) {
		UserService.validUsername(username);
		if (row.fullName() == null || row.fullName().isBlank()) {
			throw new ApiException("400", "Full name must not be empty");
		}
		String email = PersonService.normalizeEmail(row.email());
		if (role == null) {
			throw new ApiException("400", "Role \"" + (row.role() == null ? "" : row.role().trim()) + "\" does not exist");
		}
		if (!usernames.add(username) || !emails.add(email)) {
			throw new ApiException("409", "The username or email appears twice in the file");
		}
		UserEntity other = userRepository.findByUsername(username);
		if (other != null) {
			throw new ApiException("409", other.getDeletedAt() == null ? "Username already exists" : "Username belongs to a user in the trash");
		}
		if (userRepository.emailTaken(email, null)) {
			throw new ApiException("409", "The email " + email + " is already used by someone else");
		}
		UserDTO dto = new UserDTO();
		dto.setUsername(username);
		dto.setFullName(row.fullName().trim());
		dto.setEmail(email);
		RoleDTO roleDto = new RoleDTO();
		roleDto.setId(role.getId());
		dto.setRoles(Set.of(roleDto));
		return dto;
	}

	// A role is named by its id or by its name, in any letter case.
	private static RoleEntity findRole(List<RoleEntity> roles, String text) {
		String wanted = text == null ? "" : text.trim();
		return roles.stream().filter(r -> r.getId().equalsIgnoreCase(wanted) || (r.getName() != null && r.getName().equalsIgnoreCase(wanted)))
				.findFirst().orElse(null);
	}
}

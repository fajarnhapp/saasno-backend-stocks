package com.saasno.service;

import java.math.BigInteger;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.saasno.entity.PersonEntity;
import com.saasno.entity.RoleEntity;
import com.saasno.entity.SessionEntity;
import com.saasno.entity.UserEntity;
import com.saasno.repository.RoleRepository;
import com.saasno.repository.UserRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.person.PersonDTO;
import com.saasno.utility.dto.role.RoleDTO;
import com.saasno.utility.dto.user.UserDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.ResponseModel;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;
import com.saasno.utility.picker.UserStatus;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

@Service
public class UserService implements UserDetailsService {

	private static final Pattern USERNAME = Pattern.compile("^[a-z0-9][a-z0-9._-]{2,63}$");

	@Autowired
	private UserRepository userRepository;

	@Autowired
	private RoleRepository roleRepository;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private SessionService sessionService;

	@Autowired
	private PasswordPolicyService passwordPolicyService;

	@Autowired
	private AvatarService avatarService;

	@Autowired
	private TwoFactorPolicyService twoFactorPolicyService;

	@Autowired
	private PersonService personService;

	@Autowired
	private ChangeLogService changeLogService;

	@Autowired
	private PasswordResetService passwordResetService;

	@Autowired
	private com.saasno.utility.mapper.PersonMapper personMapper;

	@Autowired
	private com.saasno.utility.mapper.UserMapper userMapper;

	@Override
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User not found");
		}

		return user.details();
	}

	@Transactional
	public ResponseModel<UserDTO> findByUsername() {
		UserEntity user = userRepository.findByUsername(sessionUtility.getUsername());
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		UserDTO dto = userMapper.dto(user);
		dto.setPasswordChangeRequired(passwordPolicyService.isChangeRequired(user));
		dto.setTwoFactorRequired(twoFactorPolicyService.isRequired());
		dto.setTwoFactorSetupRequired(twoFactorPolicyService.isSetupRequired(user));
		dto.setAvatarVersion(avatarService.version(user.getId()));
		dto.setRecoveryCodesLeft(user.recoveryCodesLeft());
		return new ResponseModel<>(ResponseModel.SUCCESS, dto);
	}

	@Transactional
	public ResponseModel<UserDTO> create(UserDTO dto) {
		String username = validUsername(dto.getUsername());
		failIfTaken(username);
		if (dto.getFullName() == null || dto.getFullName().isBlank()) {
			throw new ApiException("400", "Full name must not be empty");
		}

		PersonEntity person = new PersonEntity();
		PersonDTO biodata = new PersonDTO();
		biodata.setFullName(dto.getFullName());
		personService.applyBiodata(person, biodata);
		personService.setPrimaryEmail(person, dto.getEmail());

		UserEntity user = new UserEntity(username, person, resolveRoles(dto.getRoles()));
		userRepository.store(user);
		// The new account is locked: the user chooses their own password through an emailed link. Without working email nothing is sent,
		// and an admin can send the link later with Reset password.
		boolean activation = passwordResetService.sendActivation(user);
		changeLogService.created("USER", user.getId(), user.getUsername());
		UserDTO created = userMapper.dto(user);
		created.setActivationSent(activation);
		return new ResponseModel<UserDTO>().success(created);
	}

	private void failIfTaken(String username) {
		UserEntity other = userRepository.findByUsername(username);
		if (other != null) {
			throw new ApiException("409", other.getDeletedAt() == null ? "Username already exists"
					: "Username belongs to a user in the trash; restore or delete that user for good first");
		}
	}

	static final java.util.Set<String> BULK_ACTIONS = java.util.Set.of("ACTIVATE", "DEACTIVATE", "BLOCK", "DELETE", "SEND_ACTIVATION", "SET_ROLE");

	// One step of a bulk action, in its own transaction (see UserBulkService); returns the username. Throws when it cannot be done.
	@Transactional
	public String bulkApply(String id, String action, String roleId) {
		UserEntity user = userRepository.find(id);
		if (user == null || user.getDeletedAt() != null) {
			throw new ApiException("404", "User does not exist");
		}
		boolean self = user.getUsername().equals(sessionUtility.getUsername());
		switch (action) {
			case "ACTIVATE" -> changeStatus(user, UserStatus.ACTIVED);
			case "DEACTIVATE" -> {
				refuseSelf(self, "deactivate");
				changeStatus(user, UserStatus.INACTIVED);
			}
			case "BLOCK" -> {
				refuseSelf(self, "block");
				changeStatus(user, UserStatus.BLOCKED);
			}
			case "DELETE" -> delete(id);
			case "SEND_ACTIVATION" -> {
				refuseSelf(self, "reset the password of");
				resetPassword(user.getUsername());
			}
			case "SET_ROLE" -> {
				UserSnapshot before = new UserSnapshot(user);
				user.setRoles(resolveRoles(java.util.Set.of(roleOf(roleId))));
				userRepository.update(user);
				changeLogService.updated("USER", user.getId(), user.getUsername(), before.diff(new UserSnapshot(user)));
			}
			default -> throw new ApiException("400", "Unknown action");
		}
		return user.getUsername();
	}

	private void changeStatus(UserEntity user, UserStatus status) {
		UserSnapshot before = new UserSnapshot(user);
		user.setStatus(status);
		user.clearFailedLogin();
		userRepository.update(user);
		if (status != UserStatus.ACTIVED) {
			sessionService.deleteAllByUsername(user.getUsername());
		}
		changeLogService.updated("USER", user.getId(), user.getUsername(), before.diff(new UserSnapshot(user)));
	}

	private static void refuseSelf(boolean self, String what) {
		if (self) {
			throw new ApiException("400", "You cannot " + what + " your own account");
		}
	}

	private static RoleDTO roleOf(String roleId) {
		RoleDTO role = new RoleDTO();
		role.setId(roleId);
		return role;
	}

	// Usernames are plain names for signing in (lowercase letters, digits, dot, underscore, dash), never an email address.
	static String validUsername(String raw) {
		String username = raw == null ? "" : raw.trim();
		if (username.isEmpty()) {
			throw new ApiException("400", "Username must not be empty");
		}
		if (!USERNAME.matcher(username).matches()) {
			throw new ApiException("400", "Username must be 3-64 characters: lowercase letters, digits, dot, underscore or dash, starting with a letter or digit");
		}
		return username;
	}

	@Transactional
	public ResponseEntity<DataResult<UserDTO>> read(HttpServletRequest request) {
		try {
			return list(request, "AND e.deletedAt IS NULL");
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<UserDTO>>(new DataResult<UserDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	// The users in the trash.
	@Transactional
	public ResponseEntity<DataResult<UserDTO>> trash(HttpServletRequest request) {
		try {
			return list(request, "AND e.deletedAt IS NOT NULL");
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<DataResult<UserDTO>>(new DataResult<UserDTO>(BigInteger.ZERO, BigInteger.ZERO),
					HttpStatus.OK);
		}
	}

	private ResponseEntity<DataResult<UserDTO>> list(HttpServletRequest request, String condition) {
		DataFilter filter = new DataFilter(request);
		// The condition is fixed text from this class (never from the request).
		filter.setCustom(condition);
		DataResult<UserEntity> result = userRepository.dataTable(filter);
		List<UserDTO> dto = userMapper.dto(result.getData());
		return new ResponseEntity<DataResult<UserDTO>>(new DataResult<>(dto, result.getTotal(), result.getGrandTotal()), HttpStatus.OK);
	}

	@Transactional
	public ResponseModel<UserDTO> update(UserDTO dto) {
		UserEntity existing = userRepository.find(dto.getId());
		if (existing == null || existing.getDeletedAt() != null) {
			throw new ApiException("404", "User does not exist");
		}

		UserSnapshot before = new UserSnapshot(existing);
		if (dto.getUsername() != null && !dto.getUsername().equals(existing.getUsername())) {
			String username = validUsername(dto.getUsername());
			failIfTaken(username);
			// Sessions are keyed by username, so the old ones end.
			sessionService.deleteAllByUsername(existing.getUsername());
			existing.setUsername(username);
		}
		if (dto.getFullName() != null) {
			PersonDTO biodata = personMapper.dto(existing.getPerson());
			biodata.setFullName(dto.getFullName());
			personService.applyBiodata(existing.getPerson(), biodata);
		}
		if (dto.getEmail() != null && !dto.getEmail().isBlank()) {
			personService.setPrimaryEmail(existing.getPerson(), dto.getEmail());
		}
		if (dto.getRoles() != null) {
			existing.setRoles(resolveRoles(dto.getRoles()));
		}
		if (dto.getStatus() != null) {
			existing.setStatus(UserStatus.fromString(dto.getStatus()));
			// An admin saving the user's status also clears the failed-login lock.
			existing.clearFailedLogin();
		}

		userRepository.update(existing);
		changeLogService.updated("USER", existing.getId(), existing.getUsername(), before.diff(new UserSnapshot(existing)));
		return new ResponseModel<UserDTO>().success(userMapper.dto(existing));
	}

	// Moves the user to the trash: they cannot sign in any more and are signed out, but nothing is lost and they can be restored.
	@Transactional
	public ResponseModel<UserDTO> delete(String id) {
		UserEntity existing = userRepository.find(id);
		if (existing == null || existing.getDeletedAt() != null) {
			throw new ApiException("404", "User does not exist");
		}
		if (existing.getUsername().equals(sessionUtility.getUsername())) {
			throw new ApiException("400", "You cannot delete your own account");
		}
		existing.moveToTrash(sessionUtility.getUsername());
		userRepository.update(existing);
		sessionService.deleteAllByUsername(existing.getUsername());
		changeLogService.event("USER", existing.getId(), existing.getUsername(), "moved to trash");
		return new ResponseModel<UserDTO>().success();
	}

	@Transactional
	public ResponseModel<UserDTO> restore(String id) {
		UserEntity existing = userRepository.find(id);
		if (existing == null || existing.getDeletedAt() == null) {
			throw new ApiException("404", "User is not in the trash");
		}
		existing.restore();
		userRepository.update(existing);
		changeLogService.event("USER", existing.getId(), existing.getUsername(), "restored from trash");
		return new ResponseModel<UserDTO>().success();
	}

	// Removes a user from the trash for good, with their person and contacts.
	@Transactional
	public ResponseModel<UserDTO> purge(String id) {
		UserEntity existing = userRepository.find(id);
		if (existing == null || existing.getDeletedAt() == null) {
			throw new ApiException("404", "User is not in the trash");
		}
		changeLogService.deleted("USER", existing.getId(), existing.getUsername());
		userRepository.deleteNotifications(existing.getId());
		userRepository.delete(existing);
		return new ResponseModel<UserDTO>().success();
	}

	// The fields of a user that the change history follows, taken before and after an update.
	private record UserSnapshot(String username, String fullName, String email, String status, String roles) {
		UserSnapshot(UserEntity user) {
			this(user.getUsername(), user.getPerson().getFullName(), user.getPerson().primaryEmail(),
					user.getStatus() == null ? null : user.getStatus().name(),
					user.getRoles().stream().map(RoleEntity::getName).sorted().collect(java.util.stream.Collectors.joining(", ")));
		}

		ChangeLogService.Changes diff(UserSnapshot after) {
			return ChangeLogService.changes().add("username", username, after.username).add("fullName", fullName, after.fullName)
					.add("email", email, after.email).add("status", status, after.status).add("roles", roles, after.roles);
		}
	}

	private Set<RoleEntity> resolveRoles(Set<RoleDTO> roles) {
		Set<RoleEntity> resolved = new HashSet<>();
		if (roles != null) {
			for (RoleDTO role : roles) {
				RoleEntity entity = roleRepository.find(role.getId());
				if (entity != null) {
					resolved.add(entity);
				}
			}
		}
		return resolved;
	}

	@Transactional
	public ResponseModel<UserDTO> resetPassword(String username) {
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		// The link goes first: without working email the password is left as it is, so nobody ends up locked out with no way back in.
		if (!passwordResetService.sendActivation(user)) {
			throw new ApiException("400", "The email with the link could not be sent: the user has no email address, or email is not set up");
		}
		user.lockPassword();
		user.clearFailedLogin();
		userRepository.store(user);
		sessionService.deleteAllByUsername(user.getUsername());
		changeLogService.event("USER", user.getId(), user.getUsername(), "password reset");
		UserDTO result = new UserDTO();
		result.setActivationSent(true);
		return new ResponseModel<UserDTO>().success(result);
	}

	// For a user who lost the authenticator app and the recovery codes. Their sessions end so the next login starts clean.
	@Transactional
	public ResponseModel<UserDTO> resetTwoFactor(String username) {
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		if (user.getUsername().equals(sessionUtility.getUsername())) {
			throw new ApiException("400", "Use the Security page to change your own two-factor authentication");
		}
		if (!Boolean.TRUE.equals(user.getTwoFactorAuth()) && user.getTwoFactorAuthSecret() == null) {
			throw new ApiException("409", "This user has no two-factor authentication to reset");
		}
		user.resetTwoFactor();
		userRepository.store(user);
		changeLogService.event("USER", user.getId(), user.getUsername(), "two-factor reset");
		sessionService.deleteAllByUsername(user.getUsername());
		return new ResponseModel<UserDTO>().success();
	}

	@Transactional
	public ResponseModel<UserDTO> changePassword(String password, String oldPassword) {
		String username = sessionUtility.getUsername();
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		if (!user.verifyPassword(oldPassword)) {
			throw new ApiException("400", "Old password is incorrect");
		}
		passwordPolicyService.validateNewPassword(password, user);
		user.changePassword(password);
		userRepository.store(user);
		passwordPolicyService.remember(user);

		// The current session is no longer restricted to the password change.
		SessionEntity session = sessionUtility.get();
		if (session != null && session.isPasswordChangeRequired()) {
			session.setPasswordChangeRequired(false);
			sessionService.create(session);
		}
		return new ResponseModel<>(ResponseModel.SUCCESS, userMapper.dto(user));
	}

	@Transactional
	public ResponseModel<Boolean> verifyPassword(String password) {
		String username = sessionUtility.getUsername();
		UserEntity user = userRepository.findByUsername(username);
		if (user == null) {
			throw new ApiException("404", "User not found");
		}
		if (!user.verifyPassword(password)) {
			throw new ApiException("400", "Incorrect password");
		}
		return new ResponseModel<Boolean>().success(true);
	}
}

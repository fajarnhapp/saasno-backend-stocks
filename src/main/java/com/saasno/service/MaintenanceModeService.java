package com.saasno.service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.saasno.entity.RoleEntity;
import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.maintenance.MaintenanceModeDTO;
import com.saasno.utility.dto.maintenance.MaintenanceModeUpdateDTO;
import com.saasno.utility.dto.maintenance.MaintenanceStatusDTO;
import com.saasno.utility.exception.ApiException;

import jakarta.persistence.EntityManager;

// Maintenance mode. While it is active, only users with a role in MAINTENANCE_BYPASS_ROLES (SUPER is always one of them) can sign in or
// use the app; everybody else gets 503 with the maintenance message. It is active when MAINTENANCE_MODE is true and MAINTENANCE_UNTIL (if
// set) has not passed, so a forgotten switch ends by itself. The state is cached for a few seconds, because every request asks for it.
@Service
public class MaintenanceModeService {

	public static final String ALWAYS_ALLOWED_ROLE = "SUPER";
	public static final int MAX_MESSAGE = 500;
	static final String DEFAULT_MESSAGE = "The system is under maintenance. Please try again later.";

	private static final DateTimeFormatter UNTIL = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
	private static final long CACHE_MILLIS = 5_000;

	// What is stored, resolved against the clock.
	record State(boolean enabled, boolean active, String message, LocalDateTime until, List<String> bypassRoles) {
	}

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private ConfigurationService configurationService;

	@Autowired
	private EntityManager entityManager;

	private volatile State cached;
	private volatile long cachedAt;

	State state() {
		State current = cached;
		long now = System.currentTimeMillis();
		if (current != null && now - cachedAt < CACHE_MILLIS) {
			return current;
		}
		State fresh = read();
		cached = fresh;
		cachedAt = now;
		return fresh;
	}

	void invalidate() {
		cached = null;
	}

	private State read() {
		boolean enabled = "true".equalsIgnoreCase(trim(configurationRepository.get(ConfigurationKey.MAINTENANCE_MODE)));
		LocalDateTime until = parseUntil(configurationRepository.get(ConfigurationKey.MAINTENANCE_UNTIL));
		String message = trim(configurationRepository.get(ConfigurationKey.MAINTENANCE_MESSAGE));
		boolean active = enabled && (until == null || LocalDateTime.now().isBefore(until));
		List<String> roles = new ArrayList<>(parseRoles(configurationRepository.get(ConfigurationKey.MAINTENANCE_BYPASS_ROLES)));
		return new State(enabled, active, message.isEmpty() ? DEFAULT_MESSAGE : message, until, roles);
	}

	public boolean active() {
		return state().active();
	}

	// Throws 503 when maintenance is active and none of the roles may work during it. Signing out is always possible.
	public void enforce(Collection<String> roleIds, String path, String method) {
		if (path != null && path.equals(com.saasno.utility.constant.Path.API_LOGOUT) && "DELETE".equalsIgnoreCase(method)) {
			return;
		}
		State state = state();
		if (!state.active()) {
			return;
		}
		if (roleIds != null) {
			for (String roleId : roleIds) {
				if (state.bypassRoles().contains(roleId)) {
					return;
				}
			}
		}
		throw new ApiException("503", state.message());
	}

	// What anyone may see before signing in (login page banner and the maintenance page).
	public MaintenanceStatusDTO status() {
		State state = state();
		return new MaintenanceStatusDTO(state.active(), state.active() ? state.message() : "", state.active() && state.until() != null ? formatUntil(state.until()) : null);
	}

	@Transactional(readOnly = true)
	public MaintenanceModeDTO read(boolean withRoles) {
		invalidate();
		State state = state();
		List<MaintenanceModeDTO.RoleOption> roles = new ArrayList<>();
		if (withRoles) {
			@SuppressWarnings("unchecked")
			List<RoleEntity> found = entityManager.createQuery("SELECT r FROM RoleEntity r ORDER BY r.id").getResultList();
			found.forEach(role -> roles.add(new MaintenanceModeDTO.RoleOption(role.getId(), role.getName())));
		}
		return new MaintenanceModeDTO(state.enabled(), state.active(), configurationRepository.get(ConfigurationKey.MAINTENANCE_MESSAGE) == null ? "" : trim(configurationRepository.get(ConfigurationKey.MAINTENANCE_MESSAGE)),
				state.until() == null ? null : formatUntil(state.until()), state.bypassRoles(), roles);
	}

	@Transactional
	public MaintenanceModeDTO update(MaintenanceModeUpdateDTO request) {
		if (request == null) {
			throw new ApiException("400", "Request body is required");
		}
		LocalDateTime until = parseUntil(request.until());
		if (request.until() != null && !request.until().isBlank() && until == null) {
			throw new ApiException("400", "The end time must look like 2026-10-23T18:00");
		}
		if (request.enabled() && until != null && !until.isAfter(LocalDateTime.now())) {
			throw new ApiException("400", "The end time is already past");
		}

		List<String> roles = request.bypassRoles() == null ? List.of() : request.bypassRoles();
		for (String role : roles) {
			if (role != null && !role.isBlank() && entityManager.find(RoleEntity.class, role.trim()) == null) {
				throw new ApiException("400", "Unknown role: " + role);
			}
		}

		// All values are checked before any is saved (one transaction), so a bad one leaves the old settings in place.
		configurationService.set(ConfigurationKey.MAINTENANCE_MESSAGE, request.message() == null ? "" : request.message());
		configurationService.set(ConfigurationKey.MAINTENANCE_UNTIL, until == null ? "" : formatUntil(until));
		configurationService.set(ConfigurationKey.MAINTENANCE_BYPASS_ROLES, String.join(",", roles));
		configurationService.set(ConfigurationKey.MAINTENANCE_MODE, String.valueOf(request.enabled()));
		invalidate();
		return read(true);
	}

	// ---- helpers (also used when a value is saved from the Configuration screen) ----

	// SUPER first, then the others, without duplicates.
	public static String bypassRolesValue(Collection<String> roles) {
		Set<String> result = new LinkedHashSet<>();
		result.add(ALWAYS_ALLOWED_ROLE);
		if (roles != null) {
			for (String role : roles) {
				if (role != null && !role.isBlank()) {
					result.add(role.trim());
				}
			}
		}
		return String.join(",", result);
	}

	static Set<String> parseRoles(String value) {
		Set<String> result = new LinkedHashSet<>();
		result.add(ALWAYS_ALLOWED_ROLE);
		if (value != null) {
			for (String part : value.split("[,\\s]+")) {
				if (!part.isBlank()) {
					result.add(part.trim());
				}
			}
		}
		return result;
	}

	public static LocalDateTime parseUntil(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return LocalDateTime.parse(raw.trim()).withSecond(0).withNano(0);
		} catch (DateTimeParseException e) {
			return null;
		}
	}

	public static String formatUntil(LocalDateTime until) {
		return until.format(UNTIL);
	}

	private static String trim(String value) {
		return value == null ? "" : value.trim();
	}

}

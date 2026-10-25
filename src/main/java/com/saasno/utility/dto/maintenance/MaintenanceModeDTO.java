package com.saasno.utility.dto.maintenance;

import java.util.List;

// The admin view: the saved settings, whether maintenance is active right now (it may have ended by itself), and every role to pick from.
public record MaintenanceModeDTO(boolean enabled, boolean active, String message, String until, List<String> bypassRoles, List<RoleOption> roles) {

	public record RoleOption(String id, String name) {
	}

}

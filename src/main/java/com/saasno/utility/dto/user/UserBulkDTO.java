package com.saasno.utility.dto.user;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

// Requests and answers of the bulk actions and of the CSV import on users.
public final class UserBulkDTO {

	private UserBulkDTO() {
	}

	// An action on many users: ACTIVATE, DEACTIVATE, BLOCK, DELETE (to the trash), SEND_ACTIVATION or SET_ROLE (with roleId).
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Action(List<String> ids, String action, String roleId) {
	}

	public record ActionItem(String id, String username, boolean ok, String message) {
	}

	public record ActionResult(int succeeded, int failed, List<ActionItem> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ImportRow(String username, String fullName, String email, String role) {
	}

	// dryRun = only check the rows and report, change nothing.
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record ImportRequest(List<ImportRow> rows, boolean dryRun) {
	}

	public record ImportItem(int line, String username, boolean ok, String message) {
	}

	public record ImportResult(boolean dryRun, int valid, int invalid, int created, List<ImportItem> items) {
	}
}

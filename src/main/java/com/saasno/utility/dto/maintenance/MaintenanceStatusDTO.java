package com.saasno.utility.dto.maintenance;

// What anyone may see (no login needed): is maintenance on, the message, and when it should end (null = not known).
public record MaintenanceStatusDTO(boolean active, String message, String until) {
}

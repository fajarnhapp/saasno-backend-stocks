package com.saasno.utility.dto.maintenance;

// How many records each cleanup removed.
public record CleanupResultDTO(long downloads, long auditLogs, long notifications) {
}

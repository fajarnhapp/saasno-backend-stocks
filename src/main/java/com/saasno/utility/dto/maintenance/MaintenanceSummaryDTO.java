package com.saasno.utility.dto.maintenance;

// What the Maintenance screen shows: how much is stored now, and how long each kind of data is kept (0 = forever).
public record MaintenanceSummaryDTO(long downloadJobs, long downloadBytes, long auditLogs, long notifications,
		int downloadRetentionDays, int auditLogRetentionDays, int notificationRetentionDays) {
}

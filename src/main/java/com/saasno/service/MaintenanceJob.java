package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.saasno.utility.logger.Log;

// The scheduled side of MaintenanceService. Notifications are cleaned by NotificationCleanupJob (03:30).
@Component
public class MaintenanceJob {

	@Autowired
	private MaintenanceService maintenanceService;

	@Scheduled(cron = "${saasno.download-cleanup.cron:0 45 3 * * *}")
	public void downloads() {
		try {
			long removed = maintenanceService.cleanupDownloads();
			if (removed > 0) {
				Log.info("Download cleanup removed {} old download(s)", removed);
			}
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	@Scheduled(cron = "${saasno.audit-cleanup.cron:0 0 4 * * *}")
	public void auditLogs() {
		try {
			long removed = maintenanceService.cleanupAuditLogs();
			if (removed > 0) {
				Log.info("Audit log cleanup removed {} old entrie(s)", removed);
			}
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

}

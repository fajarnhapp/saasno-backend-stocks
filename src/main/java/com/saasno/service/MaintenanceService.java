package com.saasno.service;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.repository.LogRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.dto.maintenance.CleanupResultDTO;
import com.saasno.utility.dto.maintenance.MaintenanceSummaryDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;

// Housekeeping: removes data that is no longer useful (old downloads, old audit log entries, old notifications). The scheduled jobs and
// the Maintenance screen share this code. Every step only deletes, so running it on several instances at once is harmless.
@Service
public class MaintenanceService {

	static final int DEFAULT_DOWNLOAD_DAYS = 7;
	static final int DEFAULT_AUDIT_LOG_DAYS = 365;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Autowired
	private LogRepository logRepository;

	@Autowired
	private com.saasno.repository.LoginHistoryRepository loginHistoryRepository;

	@Autowired
	private com.saasno.repository.ChangeLogRepository changeLogRepository;

	@Autowired
	private NotificationCleanupJob notificationCleanupJob;

	@Value("${app.upload.dir}")
	private String uploadDir;

	public MaintenanceSummaryDTO summary() {
		Number jobs = (Number) entityManager.createNativeQuery("SELECT count(*) FROM public.download_job").getSingleResult();
		Number notifications = (Number) entityManager.createNativeQuery("SELECT count(*) FROM public.notification").getSingleResult();
		return new MaintenanceSummaryDTO(jobs.longValue(), downloadBytes(), logRepository.count(), notifications.longValue(),
				downloadRetentionDays(), auditLogRetentionDays(), notificationRetentionDays());
	}

	public CleanupResultDTO runAll() {
		return new CleanupResultDTO(cleanupDownloads(), cleanupAuditLogs(), notificationCleanupJob.runNow());
	}

	// One kind of data at a time: "downloads", "audit-logs" or "notifications"; empty or "all" runs everything.
	public CleanupResultDTO run(String type) {
		String kind = type == null ? "" : type.trim().toLowerCase();
		return switch (kind) {
			case "", "all" -> runAll();
			case "downloads" -> new CleanupResultDTO(cleanupDownloads(), 0, 0);
			case "audit-logs" -> new CleanupResultDTO(0, cleanupAuditLogs(), 0);
			case "notifications" -> new CleanupResultDTO(0, 0, notificationCleanupJob.runNow());
			default -> throw new ApiException("400", "Unknown cleanup type: " + type + " (use downloads, audit-logs or notifications)");
		};
	}

	// Downloads older than DOWNLOAD_RETENTION_DAYS (rows and files).
	public long cleanupDownloads() {
		LocalDateTime cutoff = LocalDateTime.now().minusDays(downloadRetentionDays());
		Long removed = transactionTemplate.execute(status -> {
			@SuppressWarnings("unchecked")
			List<Object> files = entityManager.createNativeQuery(
					"SELECT file_name FROM public.download_job WHERE registered < :cutoff AND file_name IS NOT NULL")
					.setParameter("cutoff", cutoff).getResultList();
			files.forEach(file -> deleteDownloadFile(String.valueOf(file)));
			return (long) entityManager.createNativeQuery("DELETE FROM public.download_job WHERE registered < :cutoff")
					.setParameter("cutoff", cutoff).executeUpdate();
		});
		return removed == null ? 0 : removed;
	}

	// Audit log entries older than AUDIT_LOG_RETENTION_DAYS; 0 keeps them all.
	public long cleanupAuditLogs() {
		int days = auditLogRetentionDays();
		if (days <= 0) {
			return 0;
		}
		LocalDateTime cutoff = LocalDateTime.now().minusDays(days);
		// The sign-in and change history follow the same retention as the audit log.
		transactionTemplate.executeWithoutResult(status -> {
			loginHistoryRepository.deleteOlderThan(cutoff);
			changeLogRepository.deleteOlderThan(cutoff);
		});
		return logRepository.deleteOlderThan(cutoff);
	}

	// Every download of every user, plus any file left behind in the downloads folder.
	public long purgeAllDownloads() {
		Long removed = transactionTemplate.execute(status -> (long) entityManager.createNativeQuery("DELETE FROM public.download_job")
				.executeUpdate());
		Path directory = downloadsDirectory();
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
				for (Path file : files) {
					if (Files.isRegularFile(file)) {
						Files.deleteIfExists(file);
					}
				}
			} catch (IOException e) {
				Log.error("Failed to empty the downloads folder", e);
			}
		}
		return removed == null ? 0 : removed;
	}

	private long downloadBytes() {
		long total = 0;
		Path directory = downloadsDirectory();
		if (Files.isDirectory(directory)) {
			try (DirectoryStream<Path> files = Files.newDirectoryStream(directory)) {
				for (Path file : files) {
					if (Files.isRegularFile(file)) {
						total += Files.size(file);
					}
				}
			} catch (IOException e) {
				Log.error("Failed to measure the downloads folder", e);
			}
		}
		return total;
	}

	private void deleteDownloadFile(String fileName) {
		// The name comes from our own database, but is checked anyway: it must be a plain file name inside the downloads folder.
		Path directory = downloadsDirectory();
		Path file = directory.resolve(fileName).normalize();
		if (!file.startsWith(directory) || file.equals(directory)) {
			return;
		}
		try {
			Files.deleteIfExists(file);
		} catch (IOException e) {
			Log.error("Failed to delete download file " + fileName, e);
		}
	}

	private Path downloadsDirectory() {
		return Paths.get(uploadDir, "downloads").toAbsolutePath().normalize();
	}

	int downloadRetentionDays() {
		return integer(ConfigurationKey.DOWNLOAD_RETENTION_DAYS, DEFAULT_DOWNLOAD_DAYS, 1);
	}

	int auditLogRetentionDays() {
		return integer(ConfigurationKey.AUDIT_LOG_RETENTION_DAYS, DEFAULT_AUDIT_LOG_DAYS, 0);
	}

	private int notificationRetentionDays() {
		return integer(ConfigurationKey.NOTIFICATION_RETENTION_DAYS, 30, 1);
	}

	private int integer(ConfigurationKey key, int fallback, int min) {
		String value = configurationRepository.get(key);
		try {
			int parsed = value == null ? fallback : Integer.parseInt(value.trim());
			return parsed < min ? fallback : parsed;
		} catch (NumberFormatException e) {
			return fallback;
		}
	}

}

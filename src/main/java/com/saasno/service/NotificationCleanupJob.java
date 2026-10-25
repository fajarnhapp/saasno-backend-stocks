package com.saasno.service;

import java.time.LocalDateTime;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.saasno.repository.ConfigurationRepository;
import com.saasno.utility.constant.ConfigurationKey;
import com.saasno.utility.logger.Log;

import jakarta.persistence.EntityManager;

// Cleans up read notifications older than NOTIFICATION_RETENTION_DAYS (default and maximum 30 days), any notification older than a
// month, and then message bodies nobody references any more. Safe to run on several instances at once because it only deletes.
@Component
public class NotificationCleanupJob {

	private static final int DEFAULT_DAYS = 30;

	@Autowired
	private EntityManager entityManager;

	@Autowired
	private TransactionTemplate transactionTemplate;

	@Autowired
	private ConfigurationRepository configurationRepository;

	@Scheduled(cron = "${saasno.notification-cleanup.cron:0 30 3 * * *}")
	public void scheduled() {
		try {
			// Wrapped explicitly: @Transactional on a method called from inside the same class does not apply.
			transactionTemplate.executeWithoutResult(status -> cleanup());
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
		}
	}

	// Runs the cleanup right now (the Maintenance screen); returns how many notifications were removed.
	public int runNow() {
		Integer removed = transactionTemplate.execute(status -> cleanup());
		return removed == null ? 0 : removed;
	}

	private int cleanup() {
		LocalDateTime now = LocalDateTime.now();
		// Read ones are deleted after NOTIFICATION_RETENTION_DAYS (at most a month). Unread ones are not kept longer than a month either:
		// the list only shows the last month, so older ones would never be seen again.
		int notifications = entityManager.createNativeQuery(
				"DELETE FROM public.notification WHERE (status = 1 AND registered < :readCutoff) OR registered < :maxCutoff")
				.setParameter("readCutoff", now.minusDays(days())).setParameter("maxCutoff", now.minusDays(NotificationService.MAX_AGE_DAYS))
				.executeUpdate();
		entityManager.createNativeQuery("DELETE FROM public.notification_message m WHERE m.registered < :cutoff "
				+ "AND NOT EXISTS (SELECT 1 FROM public.notification n WHERE n.notification_message_id = m.id)")
				.setParameter("cutoff", now.minusDays(days())).executeUpdate();
		return notifications;
	}

	private int days() {
		String value = configurationRepository.get(ConfigurationKey.NOTIFICATION_RETENTION_DAYS);
		try {
			return Math.min(NotificationService.MAX_AGE_DAYS, Math.max(1, value == null ? DEFAULT_DAYS : Integer.parseInt(value.trim())));
		} catch (NumberFormatException e) {
			return DEFAULT_DAYS;
		}
	}

}

package com.saasno.service;

import java.math.BigInteger;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;

import com.saasno.entity.ChangeLogEntity;
import com.saasno.repository.ChangeLogRepository;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.logger.Log;
import com.saasno.utility.model.data.DataFilter;
import com.saasno.utility.model.data.DataResult;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.transaction.Transactional;

// The history of changes to records (users, people, roles, settings): what changed, from which value to which, and who did it.
// Written in the same transaction as the change, so the two stay together. Values of secret fields are never stored.
@Service
public class ChangeLogService {

	private static final Pattern SECRET = Pattern.compile("(?i).*(password|secret|token|key).*");
	private static final int MAX_VALUE = 2000;

	@Autowired
	private ChangeLogRepository changeLogRepository;

	@Autowired
	private SessionUtility sessionUtility;

	// Collects the fields of one update; only the ones that really differ are kept.
	public static class Changes {
		private final Map<String, String[]> fields = new LinkedHashMap<>();

		public Changes add(String field, Object before, Object after) {
			String oldValue = before == null ? null : String.valueOf(before);
			String newValue = after == null ? null : String.valueOf(after);
			if (!Objects.equals(oldValue, newValue)) {
				fields.put(field, new String[] { oldValue, newValue });
			}
			return this;
		}

		public boolean isEmpty() {
			return fields.isEmpty();
		}
	}

	public static Changes changes() {
		return new Changes();
	}

	public void created(String type, String id, String label) {
		store(type, id, label, "CREATE", null, null, null);
	}

	public void deleted(String type, String id, String label) {
		store(type, id, label, "DELETE", null, null, null);
	}

	public void updated(String type, String id, String label, Changes changes) {
		changes.fields.forEach((field, values) -> store(type, id, label, "UPDATE", field, values[0], values[1]));
	}

	// An action without values to compare (a password reset, for example).
	public void event(String type, String id, String label, String field) {
		store(type, id, label, "UPDATE", field, null, null);
	}

	private void store(String type, String id, String label, String action, String field, String oldValue, String newValue) {
		try {
			boolean secret = field != null && SECRET.matcher(field).matches();
			changeLogRepository.store(new ChangeLogEntity(type, id, label, action, field, cut(secret ? mask(oldValue) : oldValue),
					cut(secret ? mask(newValue) : newValue), sessionUtility.getUsername()));
		} catch (Exception e) {
			// The history must never make the change itself fail, but a gap in it is worth knowing about.
			Log.error("Failed to record the change history: " + e.getMessage());
		}
	}

	private static String mask(String value) {
		return value == null ? null : "********";
	}

	private static String cut(String value) {
		return value == null || value.length() <= MAX_VALUE ? value : value.substring(0, MAX_VALUE) + "…";
	}

	@Transactional
	public ResponseEntity<DataResult<ChangeLogEntity>> read(HttpServletRequest request) {
		try {
			return new ResponseEntity<>(changeLogRepository.dataTable(new DataFilter(request)), HttpStatus.OK);
		} catch (Exception e) {
			Log.error(e.getMessage(), e);
			return new ResponseEntity<>(new DataResult<ChangeLogEntity>(BigInteger.ZERO, BigInteger.ZERO), HttpStatus.OK);
		}
	}
}

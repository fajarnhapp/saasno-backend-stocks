package com.saasno.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import com.saasno.entity.SessionEntity;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.session.SessionDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

// List of signed-in devices and forced sign-out. A user sees and signs out their own other devices; roles allowed the "all sessions"
// API do it for every user. The session in use cannot be signed out here (use Logout), and the session id (token) is never sent:
// only its hash handle is.
@Service
public class SessionManagementService {

	@Autowired
	private SessionService sessionService;

	@Autowired
	private SessionUtility sessionUtility;

	@Autowired
	private ChangeLogService changeLogService;

	public ResponseModel<List<SessionDTO>> mine() {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		List<SessionDTO> list = new ArrayList<>();
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId())) {
				list.add(dto(s, current));
			}
		}
		list.sort(Comparator.comparing(SessionDTO::registered).reversed());
		return new ResponseModel<List<SessionDTO>>().success(list);
	}

	public ResponseModel<Void> revokeMine(String handle) {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		if (handle == null || handle.equals(current)) {
			throw new ApiException("409", "This is the device you are using; use the Logout button");
		}
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId()) && SessionService.handle(s).equals(handle)) {
				sessionService.delete(s.getId());
				return new ResponseModel<Void>().success(null);
			}
		}
		throw new ApiException("404", "Session not found");
	}

	// Mengeluarkan semua perangkat lain milik sendiri.
	public ResponseModel<Integer> revokeOthers() {
		String userId = sessionUtility.getUserId();
		String current = SessionService.handle(sessionUtility.get());
		int count = 0;
		for (SessionEntity s : sessionService.readAll()) {
			if (userId.equals(s.getUserId()) && !SessionService.handle(s).equals(current)) {
				sessionService.delete(s.getId());
				count++;
			}
		}
		return new ResponseModel<Integer>().success(count);
	}

	public ResponseModel<List<SessionDTO>> all() {
		String current = SessionService.handle(sessionUtility.get());
		List<SessionDTO> list = new ArrayList<>();
		for (SessionEntity s : sessionService.readAll()) {
			if (s.getUserId() != null) {
				list.add(dto(s, current));
			}
		}
		list.sort(Comparator.comparing(SessionDTO::username).thenComparing(SessionDTO::registered, Comparator.reverseOrder()));
		return new ResponseModel<List<SessionDTO>>().success(list);
	}

	public ResponseModel<Void> revokeAny(String handle) {
		String current = SessionService.handle(sessionUtility.get());
		if (handle == null || handle.equals(current)) {
			throw new ApiException("409", "This is the device you are using; use the Logout button");
		}
		for (SessionEntity s : sessionService.readAll()) {
			if (s.getUserId() != null && SessionService.handle(s).equals(handle)) {
				sessionService.delete(s.getId());
				return new ResponseModel<Void>().success(null);
			}
		}
		throw new ApiException("404", "Session not found");
	}

	// Signs out every session except the one in use: of all users, or only of the given user. Returns how many were signed out.
	public ResponseModel<Integer> revokeAll(String userId) {
		String current = SessionService.handle(sessionUtility.get());
		boolean everyone = userId == null || userId.isBlank();
		int count = 0;
		for (SessionEntity s : sessionService.readAll()) {
			if (s.getUserId() == null || SessionService.handle(s).equals(current)) {
				continue;
			}
			if (everyone || userId.equals(s.getUserId())) {
				sessionService.delete(s.getId());
				count++;
			}
		}
		changeLogService.event("SESSION", everyone ? "all" : userId, everyone ? "All users" : userId, count + " session(s) signed out");
		return new ResponseModel<Integer>().success(count);
	}

	private SessionDTO dto(SessionEntity s, String current) {
		return new SessionDTO(SessionService.handle(s), s.getUserId(), s.getUsername(), s.getIp(), s.getUserAgent(), s.getRegistered(),
				s.getExpired(), SessionService.handle(s).equals(current));
	}
}

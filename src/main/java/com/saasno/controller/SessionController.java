package com.saasno.controller;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.saasno.service.SessionManagementService;
import com.saasno.utility.constant.Path;
import com.saasno.utility.dto.session.SessionDTO;
import com.saasno.utility.model.ResponseModel;

@RestController
@RequestMapping(Path.API_SESSION)
public class SessionController {

	@Autowired
	private SessionManagementService sessions;

	// Own devices.
	@GetMapping
	public ResponseModel<List<SessionDTO>> mine() {
		return sessions.mine();
	}

	// Signs out one of the user's own devices (handle), or every other device when handle is empty.
	@DeleteMapping
	public Object revokeMine(@RequestParam(value = "handle", required = false) String handle) {
		return handle == null || handle.isBlank() ? sessions.revokeOthers() : sessions.revokeMine(handle);
	}

	// Sessions of every user.
	@GetMapping("/all")
	public ResponseModel<List<SessionDTO>> all() {
		return sessions.all();
	}

	// Signs out one session (handle), or every session of one user (userId), or every session of every user (neither). The session in use stays.
	@DeleteMapping("/all")
	public Object revokeAny(@RequestParam(value = "handle", required = false) String handle,
			@RequestParam(value = "userId", required = false) String userId) {
		if (handle != null && !handle.isBlank()) {
			return sessions.revokeAny(handle);
		}
		return sessions.revokeAll(userId);
	}

}

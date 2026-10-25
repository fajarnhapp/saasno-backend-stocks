package com.saasno.utility.dto.session;

import java.util.Date;

// One login session. handle = hash of the session id (the real id is a token and is never sent); current = the caller's session.
public record SessionDTO(String handle, String userId, String username, String ip, String userAgent, Date registered,
		Date expired, boolean current) {
}

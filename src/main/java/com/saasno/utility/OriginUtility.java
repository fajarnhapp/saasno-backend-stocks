package com.saasno.utility;

import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

// The browser origins allowed to call the frontend with credentials (ALLOWED_ORIGINS), such as https://app.example.com.
// Entries are separated by a comma, a semicolon, a space or a new line; an entry is a scheme, a host and an optional port, nothing else.
public final class OriginUtility {

	private OriginUtility() {
	}

	public static List<String> entries(String raw) {
		List<String> list = new ArrayList<>();
		if (raw != null) {
			for (String part : raw.split("[,;\\s]+")) {
				if (!part.isBlank()) {
					list.add(part.trim());
				}
			}
		}
		return list;
	}

	// The origin in its canonical form (lower case, no trailing slash), or null when the entry is not a plain http(s) origin.
	public static String normalize(String entry) {
		try {
			URI uri = new URI(entry.trim());
			String scheme = uri.getScheme() == null ? null : uri.getScheme().toLowerCase(Locale.ROOT);
			boolean pathOk = uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/");
			if (!("http".equals(scheme) || "https".equals(scheme)) || uri.getHost() == null || uri.getRawUserInfo() != null
					|| !pathOk || uri.getRawQuery() != null || uri.getRawFragment() != null) {
				return null;
			}
			return scheme + "://" + uri.getHost().toLowerCase(Locale.ROOT) + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
		} catch (Exception e) {
			return null;
		}
	}

	// Valid entries only, canonical and without duplicates; what the frontend gets.
	public static List<String> parse(String raw) {
		Set<String> result = new LinkedHashSet<>();
		for (String entry : entries(raw)) {
			String origin = normalize(entry);
			if (origin != null) {
				result.add(origin);
			}
		}
		return new ArrayList<>(result);
	}
}

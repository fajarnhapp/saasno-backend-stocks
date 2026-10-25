package com.saasno.utility.dto.privacy;

import java.time.LocalDateTime;
import java.util.List;

// The answers of the privacy endpoints.
public final class PrivacyDTO {

	private PrivacyDTO() {
	}

	// A version of the policy; builtIn = nobody has published one yet and this is the text that comes with the application.
	// content is in the asked language when there is one (else in the other); bilingual = both languages exist, so a language choice makes sense.
	public record Policy(int version, String content, LocalDateTime publishedAt, String publishedBy, boolean builtIn, boolean bilingual) {
	}

	public record Version(int version, LocalDateTime publishedAt, String publishedBy) {
	}

	// What the admin page shows: the version in use with both texts (the built-in ones when nothing was published), and the published versions.
	public record Admin(int version, boolean builtIn, String contentEn, String contentId, List<Version> versions) {
	}

	// The English text (content) and the Indonesian text (contentId); at least one of them.
	public record Publish(String content, String contentId) {
	}

	// What the signed-in user sees about themselves.
	public record Status(int version, boolean builtIn, Integer acceptedVersion, LocalDateTime acceptedAt, boolean needsAcceptance,
			boolean deletionPending) {
	}

	public record DeletionRequest(String id, String username, String status, LocalDateTime requestedAt, LocalDateTime handledAt, String handledBy) {
	}
}

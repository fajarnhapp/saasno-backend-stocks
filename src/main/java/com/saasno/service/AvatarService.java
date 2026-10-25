package com.saasno.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.saasno.utility.ImageProcessor;
import com.saasno.utility.SessionUtility;
import com.saasno.utility.dto.user.AvatarDTO;
import com.saasno.utility.exception.ApiException;
import com.saasno.utility.model.ResponseModel;

import java.io.IOException;
import java.time.Duration;

// Profile pictures: one 256x256 PNG per user, stored as avatars/<userId>.png (no database column needed).
@Service
public class AvatarService {

	static final String FOLDER = "avatars";
	static final int SIZE = 256;
	static final long MAX_BYTES = 2L * 1024 * 1024;

	@Autowired
	private FileStorageService fileStorage;

	@Autowired
	private SessionUtility sessionUtility;

	public ResponseModel<AvatarDTO> upload(MultipartFile file) {
		String userId = currentUserId();
		if (file == null || file.isEmpty()) {
			throw new ApiException("400", "Choose a picture first");
		}
		if (file.getSize() > MAX_BYTES) {
			throw new ApiException("413", "The picture is too large (maximum 2 MB)");
		}
		byte[] png;
		try {
			png = ImageProcessor.square(file.getBytes(), SIZE);
		} catch (IOException e) {
			throw new ApiException("400", "The file could not be read");
		}
		fileStorage.save(FOLDER, name(userId), png);
		return new ResponseModel<AvatarDTO>().success(new AvatarDTO(version(userId)));
	}

	public ResponseModel<Void> delete() {
		fileStorage.delete(FOLDER, name(currentUserId()));
		return new ResponseModel<Void>().success();
	}

	// The picture of the given user, or of the signed-in user when no id is given.
	public ResponseEntity<byte[]> read(String id) {
		byte[] png = fileStorage.read(FOLDER, name(id == null || id.isBlank() ? currentUserId() : id));
		if (png == null) {
			throw new ApiException("404", "No profile picture");
		}
		return ResponseEntity.ok()
				.contentType(MediaType.IMAGE_PNG)
				.header("X-Content-Type-Options", "nosniff")
				.cacheControl(CacheControl.maxAge(Duration.ofDays(1)).cachePrivate())
				.body(png);
	}

	public long version(String userId) {
		return fileStorage.version(FOLDER, name(userId));
	}

	private String currentUserId() {
		String userId = sessionUtility.getUserId();
		if (userId == null) {
			throw new ApiException("401", "Please log in first");
		}
		return userId;
	}

	private static String name(String userId) {
		return userId + ".png";
	}

}

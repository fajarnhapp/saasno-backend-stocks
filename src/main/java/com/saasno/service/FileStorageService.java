package com.saasno.service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.saasno.utility.exception.ApiException;
import com.saasno.utility.logger.Log;

// Files kept on disk under app.upload.dir. Callers give a folder and a name that the application itself chose (never a name from the
// client), and every path is checked to stay inside the upload directory.
@Service
public class FileStorageService {

	private static final Pattern SAFE_PART = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$");

	@Value("${app.upload.dir}")
	private String uploadDir;

	// Written to a temporary file first and then moved, so a reader never sees a half-written file.
	public void save(String folder, String name, byte[] content) {
		Path target = resolve(folder, name);
		try {
			Files.createDirectories(target.getParent());
			Path temporary = Files.createTempFile(target.getParent(), ".upload-", ".tmp");
			try {
				Files.write(temporary, content);
				Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} finally {
				Files.deleteIfExists(temporary);
			}
		} catch (IOException e) {
			Log.error("Failed to store file " + folder + "/" + name, e);
			throw new ApiException("500", "Failed to store the file");
		}
	}

	public byte[] read(String folder, String name) {
		try {
			Path path = resolve(folder, name);
			return Files.exists(path) ? Files.readAllBytes(path) : null;
		} catch (IOException e) {
			Log.error("Failed to read file " + folder + "/" + name, e);
			return null;
		}
	}

	public boolean exists(String folder, String name) {
		return Files.isRegularFile(resolve(folder, name));
	}

	// Last change time in milliseconds (0 when missing); used as a version so browsers reload a changed picture.
	public long version(String folder, String name) {
		try {
			Path path = resolve(folder, name);
			return Files.exists(path) ? Files.getLastModifiedTime(path).toMillis() : 0L;
		} catch (IOException e) {
			return 0L;
		}
	}

	public void delete(String folder, String name) {
		try {
			Files.deleteIfExists(resolve(folder, name));
		} catch (IOException e) {
			Log.error("Failed to delete file " + folder + "/" + name, e);
		}
	}

	private Path resolve(String folder, String name) {
		if (folder == null || name == null || !SAFE_PART.matcher(folder).matches() || !SAFE_PART.matcher(name).matches()) {
			throw new ApiException("400", "Invalid file name");
		}
		Path root = Paths.get(uploadDir).toAbsolutePath().normalize();
		Path path = root.resolve(folder).resolve(name).normalize();
		if (!path.startsWith(root)) {
			throw new ApiException("400", "Invalid file name");
		}
		return path;
	}

}

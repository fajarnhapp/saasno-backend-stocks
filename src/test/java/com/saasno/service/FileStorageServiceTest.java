package com.saasno.service;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.test.util.ReflectionTestUtils;

import com.saasno.utility.exception.ApiException;

class FileStorageServiceTest {

	@TempDir
	Path root;

	private FileStorageService storage;

	@BeforeEach
	void setUp() {
		storage = new FileStorageService();
		ReflectionTestUtils.setField(storage, "uploadDir", root.toString());
	}

	@Test
	void savesReadsAndDeletes() {
		storage.save("avatars", "u1.png", new byte[] { 1, 2, 3 });
		assertTrue(storage.exists("avatars", "u1.png"));
		assertArrayEquals(new byte[] { 1, 2, 3 }, storage.read("avatars", "u1.png"));
		assertTrue(storage.version("avatars", "u1.png") > 0);

		storage.save("avatars", "u1.png", new byte[] { 9 });
		assertArrayEquals(new byte[] { 9 }, storage.read("avatars", "u1.png"));

		storage.delete("avatars", "u1.png");
		assertFalse(storage.exists("avatars", "u1.png"));
		assertNull(storage.read("avatars", "u1.png"));
		assertEquals(0L, storage.version("avatars", "u1.png"));
	}

	@Test
	void leavesNoTemporaryFilesBehind() throws Exception {
		storage.save("avatars", "u2.png", new byte[] { 1 });
		try (var files = java.nio.file.Files.list(root.resolve("avatars"))) {
			assertEquals(1, files.count());
		}
	}

	@Test
	void refusesNamesThatEscapeTheFolder() {
		for (String bad : new String[] { "../x.png", "..", "a/b.png", "a\\b.png", ".hidden", "", " ", "x".repeat(200) }) {
			assertThrows(ApiException.class, () -> storage.save("avatars", bad, new byte[] { 1 }), bad);
			assertThrows(ApiException.class, () -> storage.read("avatars", bad), bad);
		}
		assertThrows(ApiException.class, () -> storage.save("../outside", "a.png", new byte[] { 1 }));
		assertThrows(ApiException.class, () -> storage.save(null, "a.png", new byte[] { 1 }));
		assertThrows(ApiException.class, () -> storage.save("avatars", null, new byte[] { 1 }));
	}

}

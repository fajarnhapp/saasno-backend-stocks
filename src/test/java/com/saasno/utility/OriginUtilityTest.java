package com.saasno.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.List;

import org.junit.jupiter.api.Test;

class OriginUtilityTest {

	@Test
	void normalizesPlainOrigins() {
		assertEquals("https://app.example.com", OriginUtility.normalize("HTTPS://App.Example.com/"));
		assertEquals("http://localhost:3000", OriginUtility.normalize(" http://localhost:3000 "));
	}

	@Test
	void rejectsAnythingThatIsNotAnOrigin() {
		assertNull(OriginUtility.normalize("app.example.com"));
		assertNull(OriginUtility.normalize("ftp://app.example.com"));
		assertNull(OriginUtility.normalize("https://app.example.com/path"));
		assertNull(OriginUtility.normalize("https://user@app.example.com"));
		assertNull(OriginUtility.normalize("https://app.example.com?x=1"));
		assertNull(OriginUtility.normalize("*"));
	}

	@Test
	void parseSplitsOnCommaSemicolonSpaceAndNewlineDropsInvalidAndDuplicates() {
		assertEquals(List.of("https://a.com", "http://b.com:8080", "https://c.com"),
				OriginUtility.parse("https://a.com, http://b.com:8080;\nhttps://c.com https://A.com/ nonsense"));
		assertEquals(List.of(), OriginUtility.parse(null));
		assertEquals(List.of(), OriginUtility.parse("  "));
	}
}

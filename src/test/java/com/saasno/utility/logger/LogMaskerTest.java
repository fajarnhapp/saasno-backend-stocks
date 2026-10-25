package com.saasno.utility.logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LogMaskerTest {

	@Test
	void masksPasswordFieldsInBodiesAndQueries() {
		assertEquals("{\"username\":\"a\",\"password\":\"******\"}", LogMasker.body("{\"username\":\"a\",\"password\":\"hunter2\"}"));
		assertEquals("password=******&x=1", LogMasker.queryString("password=hunter2&x=1"));
		assertNull(LogMasker.body(""));
	}

	@Test
	void masksTheValueOfAMailPasswordConfigurationChange() {
		String masked = LogMasker.body("{\"id\":\"MAIL_PASSWORD\",\"value\":\"xsmtpsib-secret\"}");
		assertFalse(masked.contains("xsmtpsib-secret"));
		assertTrue(masked.contains("\"id\":\"MAIL_PASSWORD\""));
	}

	@Test
	void leavesTheValueOfOtherConfigurationChangesAlone() {
		assertEquals("{\"id\":\"MAIL_HOST\",\"value\":\"smtp.example.com\"}", LogMasker.body("{\"id\":\"MAIL_HOST\",\"value\":\"smtp.example.com\"}"));
	}
}

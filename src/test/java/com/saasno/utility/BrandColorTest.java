package com.saasno.utility;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class BrandColorTest {

	@Test
	void producesAValidHexColour() {
		for (int hue = 0; hue <= 360; hue += 30) {
			assertTrue(BrandColor.hex(hue, 1.0).matches("#[0-9a-f]{6}"), "hue " + hue);
		}
	}

	@Test
	void theDefaultVioletIsViolet() {
		String hex = BrandColor.hex(293, 1.0);
		int red = Integer.parseInt(hex.substring(1, 3), 16);
		int green = Integer.parseInt(hex.substring(3, 5), 16);
		int blue = Integer.parseInt(hex.substring(5, 7), 16);
		assertTrue(blue > green && red > green, "violet has little green: " + hex);
	}

	@Test
	void differentHuesGiveDifferentColoursAndLowerChromaIsGreyer() {
		assertNotEquals(BrandColor.hex(293, 1.0), BrandColor.hex(150, 1.0));
		assertEquals(BrandColor.hex(150, 1.0), BrandColor.hex(150, 1.0));
		String vivid = BrandColor.hex(150, 1.0);
		String muted = BrandColor.hex(150, 0.3);
		assertNotEquals(vivid, muted);
	}

}

package com.saasno.utility;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkUtilityTest {

	@Test
	void anEmptyListAllowsEveryone() {
		assertTrue(NetworkUtility.allowed("203.0.113.9", ""));
		assertTrue(NetworkUtility.allowed("203.0.113.9", null));
		assertTrue(NetworkUtility.allowed(null, "  "));
	}

	@Test
	void matchesSingleAddressesAndCidrRanges() {
		String list = "203.0.113.7, 10.0.0.0/8 192.168.1.0/24";
		assertTrue(NetworkUtility.allowed("203.0.113.7", list));
		assertFalse(NetworkUtility.allowed("203.0.113.8", list));
		assertTrue(NetworkUtility.allowed("10.200.3.4", list));
		assertTrue(NetworkUtility.allowed("192.168.1.254", list));
		assertFalse(NetworkUtility.allowed("192.168.2.1", list));
	}

	@Test
	void handlesIpv6AndMappedAddresses() {
		assertTrue(NetworkUtility.allowed("0:0:0:0:0:0:0:1", "::1"));
		assertTrue(NetworkUtility.allowed("2001:db8::5", "2001:db8::/32"));
		assertFalse(NetworkUtility.allowed("2001:db9::5", "2001:db8::/32"));
		assertTrue(NetworkUtility.allowed("::ffff:10.1.2.3", "10.0.0.0/8"));
		assertFalse(NetworkUtility.allowed("10.1.2.3", "2001:db8::/32"));
	}

	@Test
	void anUnknownOrInvalidClientNeverMatchesARestrictedList() {
		assertFalse(NetworkUtility.allowed(null, "10.0.0.0/8"));
		assertFalse(NetworkUtility.allowed("not-an-ip", "10.0.0.0/8"));
		assertFalse(NetworkUtility.allowed("example.com", "10.0.0.0/8"));
	}

	@Test
	void validatesEntries() {
		assertTrue(NetworkUtility.validEntry("10.0.0.1"));
		assertTrue(NetworkUtility.validEntry("10.0.0.0/8"));
		assertTrue(NetworkUtility.validEntry("2001:db8::/32"));
		assertFalse(NetworkUtility.validEntry("10.0.0.0/33"));
		assertFalse(NetworkUtility.validEntry("10.0.0.0/-1"));
		assertFalse(NetworkUtility.validEntry("10.0.0.0/abc"));
		assertFalse(NetworkUtility.validEntry("localhost"));
		assertFalse(NetworkUtility.validEntry("999.1.1.1"));
	}

}

package com.saasno.filter;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

class MetricsRequestMatcherTest {

	private static MockHttpServletRequest request(String method, String path, int localPort) {
		MockHttpServletRequest request = new MockHttpServletRequest(method, path);
		request.setLocalPort(localPort);
		return request;
	}

	@Test
	void matchesOnlyTheScrapeOnTheManagementPort() {
		MetricsRequestMatcher matcher = new MetricsRequestMatcher(8081, 8000);
		assertTrue(matcher.matches(request("GET", "/actuator/prometheus", 8081)));
		assertFalse(matcher.matches(request("GET", "/actuator/prometheus", 8000)));
		assertFalse(matcher.matches(request("GET", "/actuator/env", 8081)));
		assertFalse(matcher.matches(request("POST", "/actuator/prometheus", 8081)));
		assertFalse(matcher.matches(request("GET", "/api/user", 8081)));
	}

	@Test
	void neverMatchesWhenThereIsNoSeparateManagementPort() {
		assertFalse(new MetricsRequestMatcher(-1, 8000).matches(request("GET", "/actuator/prometheus", 8000)));
		assertFalse(new MetricsRequestMatcher(8000, 8000).matches(request("GET", "/actuator/prometheus", 8000)));
	}
}

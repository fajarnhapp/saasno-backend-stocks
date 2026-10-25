package com.saasno.filter;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.web.util.matcher.RequestMatcher;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;

// Matches the Prometheus scrape (GET /actuator/prometheus) when it arrives on the separate management port, and nothing else.
// Those requests skip the login check; on the application port the same path stays behind it, and when no separate management port
// is configured nothing matches, so the metrics can never become public by accident.
@Component
public class MetricsRequestMatcher implements RequestMatcher {

	static final String PATH = "/actuator/prometheus";

	private final int managementPort;
	private final int serverPort;

	public MetricsRequestMatcher(@Value("${management.server.port:-1}") int managementPort, @Value("${server.port:8000}") int serverPort) {
		this.managementPort = managementPort;
		this.serverPort = serverPort;
	}

	@Override
	public boolean matches(HttpServletRequest request) {
		return managementPort > 0 && managementPort != serverPort && request.getLocalPort() == managementPort
				&& HttpMethod.GET.matches(request.getMethod()) && PATH.equals(request.getRequestURI());
	}
}

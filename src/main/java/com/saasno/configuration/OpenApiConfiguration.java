package com.saasno.configuration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;

// API documentation. Switched off unless SAASNO_API_DOCS_ENABLED=true (see application.properties), so production does not publish it.
// Authorize with the session id returned by POST /api/login as a Bearer token.
@Configuration
public class OpenApiConfiguration {

	private static final String SCHEME = "session";

	@Bean
	public OpenAPI openApi(@Value("${spring.application.name}") String name, @Value("${app.version:unknown}") String version) {
		return new OpenAPI()
				.info(new Info().title(name).version(version)
						.description("Log in with POST /api/login, then authorize with the returned session id as a Bearer token."))
				.components(new Components().addSecuritySchemes(SCHEME,
						new SecurityScheme().type(SecurityScheme.Type.HTTP).scheme("bearer")))
				.addSecurityItem(new SecurityRequirement().addList(SCHEME));
	}

}

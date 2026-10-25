package com.saasno;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.boot.test.context.SpringBootTest;

// Needs the real PostgreSQL (with the schema), Redis, RabbitMQ and MongoDB, so it only runs when SAASNO_INTEGRATION_TEST=true.
@SpringBootTest
@EnabledIfEnvironmentVariable(named = "SAASNO_INTEGRATION_TEST", matches = "true")
class SaasnoBackendApplicationTests {

	@Test
	void contextLoads() {
	}

}

package com.saasno.service;

import java.sql.Connection;
import java.util.LinkedHashMap;
import java.util.Map;

import javax.sql.DataSource;

import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Service;

import com.saasno.utility.logger.Log;

import org.bson.Document;

// Liveness/readiness of the services this backend depends on. Only UP/DOWN is reported; error details stay in the log.
@Service
public class HealthService {

	@Autowired
	private DataSource dataSource;

	@Autowired
	private RedisConnectionFactory redisConnectionFactory;

	@Autowired
	private MongoTemplate mongoTemplate;

	@Autowired
	private ConnectionFactory rabbitConnectionFactory;

	public Map<String, String> check() {
		Map<String, String> components = new LinkedHashMap<>();
		components.put("postgres", status("postgres", () -> {
			try (Connection connection = dataSource.getConnection()) {
				return connection.isValid(2);
			}
		}));
		components.put("redis", status("redis", () -> {
			try (var connection = redisConnectionFactory.getConnection()) {
				return "PONG".equalsIgnoreCase(connection.ping());
			}
		}));
		components.put("mongodb", status("mongodb", () -> mongoTemplate.executeCommand(new Document("ping", 1)).get("ok") != null));
		components.put("rabbitmq", status("rabbitmq", () -> {
			var connection = rabbitConnectionFactory.createConnection();
			try {
				return connection.isOpen();
			} finally {
				connection.close();
			}
		}));
		return components;
	}

	public static boolean isUp(Map<String, String> components) {
		return components.values().stream().allMatch("UP"::equals);
	}

	private interface Probe {
		boolean run() throws Exception;
	}

	private String status(String name, Probe probe) {
		try {
			return probe.run() ? "UP" : "DOWN";
		} catch (Exception e) {
			Log.warn("Health check failed for " + name + ": " + e.getMessage());
			return "DOWN";
		}
	}

}

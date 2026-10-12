package com.payflow;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * Shared Testcontainers for Phase 1. Kafka is intentionally omitted to keep
 * CI/local verify lighter; Kafka Compose profile is verified separately.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	private static final DockerImageName POSTGRES = DockerImageName.parse("postgres:16.6-alpine");
	private static final DockerImageName REDIS = DockerImageName.parse("redis:7.4.2-alpine");

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		return new PostgreSQLContainer(POSTGRES);
	}

	@Bean
	@ServiceConnection(name = "redis")
	GenericContainer<?> redisContainer() {
		return new GenericContainer<>(REDIS).withExposedPorts(6379);
	}

}

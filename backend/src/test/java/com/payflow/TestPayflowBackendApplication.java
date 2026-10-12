package com.payflow;

import org.springframework.boot.SpringApplication;

/**
 * Convenience entrypoint to run the app against Testcontainers (Postgres + Redis).
 * Not used by {@code mvn verify}; for local exploratory runs only.
 */
public final class TestPayflowBackendApplication {

	private TestPayflowBackendApplication() {
	}

	public static void main(String[] args) {
		SpringApplication.from(PayflowBackendApplication::main)
				.with(TestcontainersConfiguration.class)
				.run(args);
	}

}
